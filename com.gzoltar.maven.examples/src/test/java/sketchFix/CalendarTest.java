package sketchFix;

import calendar.CalendarLogic;
import org.junit.Test;

import java.util.GregorianCalendar;

import static java.util.GregorianCalendar.*;
import static org.junit.Assert.assertTrue;

public class CalendarTest {

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
