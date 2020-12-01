package calendar;

import com.pholser.junit.quickcheck.*;
import com.pholser.junit.quickcheck.generator.*;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import static java.util.GregorianCalendar.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@RunWith(JQF.class)
public class CalendarTest {

    @Fuzz
    public void testLeapYear(@From(CalendarGenerator.class) GregorianCalendar cal){
        assumeTrue(cal.get(MONTH) == FEBRUARY);
        assumeTrue(cal.get(DAY_OF_MONTH) == 29);

        assertTrue(cal.get(YEAR) + " should be a leap year", CalendarLogic.isLeapYear(cal));
    }

    @Fuzz
    public void testCompare(@Size(max=100) List<@From(CalendarGenerator.class) GregorianCalendar> cals){
        Collections.sort(cals, CalendarLogic::compare);

        for (int i=1; i < cals.size(); i++){
            Calendar c1 = cals.get(i-1);
            Calendar c2 = cals.get(i);

            assumeFalse(c1.equals(c2));
            assumeTrue(c1.getTimeZone().equals(c2.getTimeZone()));

            assertTrue(c1 + " should be before " + c2, c1.before(c2));
        }
    }
    @Test
    public void testLeapYearGzoltar(){
        GregorianCalendar cal = new GregorianCalendar();
        cal.setLenient(true);

        cal.set(DAY_OF_MONTH, 31);
        cal.set(MONTH, 2);
        cal.set(YEAR, 226541200);

        assertTrue(cal.get(YEAR) + " should be a leap year", CalendarLogic.isLeapYear(cal));
    }

}
