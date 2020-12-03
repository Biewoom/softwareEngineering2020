/*
 * Copyright 2009 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Optimization for functions that have {@code var_args} or access the
 * arguments array.
 *
 * <p>Example:
 * <pre>
 * function() { alert(arguments[0] + argument[1]) }
 * </pre>
 * to:
 * <pre>
 * function(a, b) { alert(a, b) }
 * </pre>
 *
 * Each newly inserted variable name will be unique very much like the output
 * of the AST found after the {@link Normalize} pass.
 *
 */
class OptimizeArgumentsArray implements CompilerPass, ScopedCallback {

  // The arguments object as described by ECMAScript version 3
  // section 10.1.8
  private static final String ARGUMENTS = "arguments";

  // To ensure that the newly introduced parameter names are unique. We will
  // use this string as prefix unless the caller specify a different prefix.
  private static final String PARAMETER_PREFIX =
      "JSCompiler_OptimizeArgumentsArray_p";

  // The prefix for the newly introduced parameter name.
  private final String paramPrefix;

  // To make each parameter name unique in the function. We append an
  // unique integer at the end.
  private int uniqueId = 0;

  // Reference to the compiler object to notify any changes to source code AST.
  private final AbstractCompiler compiler;

  // A stack of arguments access list to the corresponding outer functions.
  private final Deque<List<Node>> argumentsAccessStack = new ArrayDeque<>();

  // This stores a list of argument access in the current scope.
  private List<Node> currentArgumentsAccess = null;

  /**
   * Construct this pass and use {@link #PARAMETER_PREFIX} as the prefix for
   * all parameter names that it introduces.
   */
  OptimizeArgumentsArray(AbstractCompiler compiler) {
    this(compiler, PARAMETER_PREFIX);
  }

  /**
   * @param paramPrefix the prefix to use for all parameter names that this
   *     pass introduces
   */
  OptimizeArgumentsArray(AbstractCompiler compiler, String paramPrefix) {
    this.compiler = checkNotNull(compiler);
    this.paramPrefix = checkNotNull(paramPrefix);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, checkNotNull(root), this);
  }

  @Override
  public void enterScope(NodeTraversal traversal) {
    checkNotNull(traversal);

    // This optimization is valid only within a function so we are going to
    // skip over the initial entry to the global scope.
    Node function = traversal.getScopeRoot();
    if (!function.isFunction()) {
      return;
    }

    // Introduces a new access list and stores the access list of the outer
    // scope in the stack if necessary.
    if (currentArgumentsAccess != null) {
      argumentsAccessStack.push(currentArgumentsAccess);
    }
    currentArgumentsAccess = new LinkedList<>();
  }

  @Override
  public void exitScope(NodeTraversal traversal) {
    checkNotNull(traversal);

    // This is the case when we are exiting the global scope where we had never
    // collected argument access list. Since we do not perform this optimization
    // for the global scope, we will skip this exit point.
    if (currentArgumentsAccess == null) {
      return;
    }

    Node function = traversal.getScopeRoot();
    if (!function.isFunction()) {
      return;
    } else if (function.isArrowFunction()) {
      return;
    }

    // Attempt to replace the argument access and if the AST has been change,
    // report back to the compiler.
    if (tryReplaceArguments(traversal.getScope())) {
      traversal.reportCodeChange();
    }

    // After the attempt to replace the arguments. The currentArgumentsAccess
    // is stale and as we exit the Scope, no longer holds all the access to the
    // current scope anymore. We'll pop the access list from the outer scope
    // and set it as currentArgumentsAccess if the outer scope is not the global
    // scope.
    if (!argumentsAccessStack.isEmpty()) {
      currentArgumentsAccess = argumentsAccessStack.pop();
    } else {
      currentArgumentsAccess = null;
    }
  }

  @Override
  public boolean shouldTraverse(
      NodeTraversal nodeTraversal, Node node, Node parent) {
    // We will continuously recurse down the AST regardless of the node types.
    return true;
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    checkNotNull(traversal);
    checkNotNull(node);

    // Searches for all the references to the arguments array.

    // We don't have an arguments list set up for this scope. This implies we
    // are currently in the global scope so we will not record any arguments
    // array access.
    if (currentArgumentsAccess == null) {
      return;
    }

    // Otherwise, we are in a function scope and we should record if the current
    // name is referring to the implicit arguments array.
    if (node.isName() && ARGUMENTS.equals(node.getString())) {
      currentArgumentsAccess.add(node);
    }
  }

  /**
   * Tries to optimize all the arguments array access in this scope by assigning
   * a name to each element.
   *
   * @param scope scope of the function
   * @return true if any modification has been done to the AST
   */
  private boolean tryReplaceArguments(Scope scope) {
    Node parametersList = scope.getRootNode().getSecondChild();
    checkState(parametersList.isParamList());

    // Keep track of rather this function modified the AST and needs to be
    // reported back to the compiler later.

    // Number of parameter that can be accessed without using the arguments
    // array.
    int numParameters = parametersList.getChildCount();

    // We want to guess what the highest index that has been access from the
    // arguments array. We will guess that it does not use anything index higher
    // than the named parameter list first until we see other wise.
    int highestIndex = numParameters - 1;
    highestIndex = getHighestIndex(highestIndex);
    if (highestIndex < 0) {
      return false;
    }

    // Number of extra arguments we need.
    // For example: function() { arguments[3] } access index 3 so
    // it will need 4 extra named arguments to changed into:
    // function(a,b,c,d) { d }.
    int numExtraArgs = highestIndex - numParameters + 1;

    // Temporary holds the new names as string for quick access later.
    String[] argNames = new String[numExtraArgs];

    boolean changed = false;
    boolean changedSignature = changeMethodSignature(numExtraArgs, parametersList, argNames);
    boolean changedBody = changeBody(numParameters, argNames, parametersList);
    changed = changedSignature;
    if (changedBody) {
      changed = changedBody;
    }
    return changed;
  }

  /**
   * Iterate through all the references to arguments array in the
   * function to determine the real highestIndex. Returns -1 when we should not
   * be replacing any arguments for this scope - we should exit tryReplaceArguments
   *
   * @param highestIndex highest index that has been accessed from the arguments array
   */
  private int getHighestIndex(int highestIndex) {
    for (Node ref : currentArgumentsAccess) {

      Node getElem = ref.getParent();

      // Bail on anything but argument[c] access where c is a constant.
      // TODO(user): We might not need to bail out all the time, there might
      // be more cases that we can cover.
      if (!getElem.isGetElem() || ref != getElem.getFirstChild()) {
        return -1;
      }

      Node index = ref.getNext();

      // We have something like arguments[x] where x is not a constant. That
      // means at least one of the access is not known.
      if (!index.isNumber() || index.getDouble() < 0) {
        // TODO(user): Its possible not to give up just yet. The type
        // inference did a 'semi value propagation'. If we know that string
        // is never a subclass of the type of the index. We'd know that
        // it is never 'callee'.
        return -1; // Give up.
      }

      //We want to bail out if someone tries to access arguments[0.5] for example
      if (index.getDouble() != Math.floor(index.getDouble())){
        return -1;
      }

      Node getElemParent = getElem.getParent();
      // When we have argument[0](), replacing it with a() is semantically
      // different if argument[0] is a function call that refers to 'this'
      if (getElemParent.isCall() && getElemParent.getFirstChild() == getElem) {
        // TODO(user): We can consider using .call() if aliasing that
        // argument allows shorter alias for other arguments.
        return -1;
      }

      // Replace the highest index if we see an access that has a higher index
      // than all the one we saw before.
      int value = (int) index.getDouble();
      if (value > highestIndex) {
        highestIndex = value;
      }
    }
    return highestIndex;
  }

  /**
   * Insert the formal parameter to the method's signature. Example: function() -->
   * function(r0, r1, r2)
   *
   * @param numExtraArgs num extra method parameters needed to replace all the arguments[i]
   * @param parametersList node representing the function signature
   * @param argNames holds the replacement names in a String array
   */
  private boolean changeMethodSignature(int numExtraArgs, Node parametersList, String[] argNames) {

    boolean changed = false;

    for (int i = 0; i < numExtraArgs; i++) {
      String name = getNewName();
      argNames[i] = name;
      parametersList.addChildToBack(
          IR.name(name).useSourceInfoIfMissingFrom(parametersList));
      changed = true;
    }
    return changed;
  }

  /**
   * Performs the replacement of arguments[x] -> a if x is known.
   */
  private boolean changeBody(int numNamedParameter, String[] argNames, Node parametersList) {
    boolean changed = false;
    boolean nextArguments = true;

    while (nextArguments) {

      nextArguments = false;

      for (Node ref : currentArgumentsAccess) {
        if (NodeUtil.getEnclosingFunction(ref).isArrowFunction()) {
          nextArguments = true;
        }

        Node index = ref.getNext();
        Node grandParent = ref.getGrandparent();
        Node parent = ref.getParent();

        // Skip if it is unknown.
        if (!index.isNumber()) {
          continue;
        }
        int value = (int) index.getDouble();

        // Unnamed parameter.
        if (value >= numNamedParameter) {
          grandParent.replaceChild(parent, IR.name(argNames[value - numNamedParameter]));
          compiler.reportChangeToEnclosingScope(grandParent);
        } else {

          // Here, for no apparent reason, the user is accessing a named parameter
          // with arguments[idx]. We can replace it with the actual name for them.
          Node name = parametersList.getFirstChild();

          // This is a linear search for the actual name from the signature.
          // It is not necessary to make this fast because chances are the user
          // will not deliberately write code like this.
          for (int i = 0; i < value; i++) {
            name = parametersList.getChildAtIndex(value);
          }
          grandParent.replaceChild(parent, IR.name(name.getString()));
          compiler.reportChangeToEnclosingScope(grandParent);
        }
        changed = true;
      }

      if (nextArguments) {
        // After the attempt to replace the arguments. The currentArgumentsAccess
        // is stale and as we exit the Scope, no longer holds all the access to the
        // current scope anymore. We'll pop the access list from the outer scope
        // and set it as currentArgumentsAccess if the outer scope is not the global
        // scope.
        if (!argumentsAccessStack.isEmpty()) {
          currentArgumentsAccess = argumentsAccessStack.pop();
        } else {
          currentArgumentsAccess = null;
        }
      }
    }

    return changed;
  }

  /**
   * Generate a unique name for the next parameter.
   */
  private String getNewName() {
    return paramPrefix + uniqueId++;
  }
}