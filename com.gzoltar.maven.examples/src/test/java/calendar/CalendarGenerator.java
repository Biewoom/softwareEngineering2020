package calendar;

import java.util.TimeZone;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;


import static java.util.GregorianCalendar.*;
import java.util.GregorianCalendar;



public class CalendarGenerator extends Generator<GregorianCalendar> {

    public CalendarGenerator() {
        super(GregorianCalendar.class);
    }

    @Override
    public GregorianCalendar generate(SourceOfRandomness randomness, GenerationStatus generationStatus) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setLenient(true);

        cal.set(DAY_OF_MONTH, randomness.nextInt(31) + 1);
        cal.set(MONTH, randomness.nextInt(12) + 1);
        cal.set(YEAR, randomness.nextInt(cal.getMinimum(YEAR), cal.getMaximum(YEAR)));
        if (randomness.nextBoolean()){
            cal.set(HOUR, randomness.nextInt(24) );
            cal.set(MINUTE, randomness.nextInt(60));
            cal.set(SECOND, randomness.nextInt(60));
        }

        String[] allTzids = TimeZone.getAvailableIDs();
        String tzid = randomness.choose(allTzids);
        TimeZone tz = TimeZone.getTimeZone(tzid);

        cal.setTimeZone(tz);

        return cal;
    }
}
