// TestDate
import java.lang.*;
import java.util.*;
import java.text.*;

/**
 * Test TJD/SOD to a date.
 */
public class TestDate
{

	public static void main(String args[])
	{
		Calendar calendar = null;
		TimeZone timeZone = null;
		DateFormat dateFormat = null;
		Date testDate = null;
		Date nowDate = null;
		int tjd=0,sod=0;

		if(args.length != 2)
		{
			System.err.println("java TestDate <tjd> <sod>");
			System.err.println("SOD is in centiseconds (seconds * 100)");
			System.err.println("Original date format:yyyy-MM-dd'T'HH:mm:ss");
			System.exit(1);
		}
		try
		{
			tjd = Integer.parseInt(args[0]);
		}
		catch(Exception e)
		{
			System.err.println("Parsing TJD failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}
		try
		{
			sod = Integer.parseInt(args[1]);
		}
		catch(Exception e)
		{
			System.err.println("Parsing SOD failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.out.println("TJD "+tjd+" SOD "+sod);
		try
		{
			testDate = truncatedJulianDateSecondOfDayToDate(tjd,sod);
		}
		catch(Exception e)
		{
			System.err.println("Creating testDate failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		System.out.println("Test Date:"+dateFormat.format(testDate));
		timeZone = TimeZone.getTimeZone("GMT+0");
		dateFormat.setTimeZone(timeZone);
		System.out.println("Test Date (GMT+0):"+dateFormat.format(testDate));
		nowDate = new Date();
		System.out.println("Test Now/Burst Date (GMT+0):"+dateFormat.format(nowDate));
		System.exit(0);
	}

	// copied from GCNDatagramScriptStarter.java
	/**
	 * Return a Java Date for the specified input fields.
	 * @param tjd Truncated Julian Date, TJD=12640 is 01 Jan 2003.
	 * @param sod Actually centi-seconds in the day, (seconds * 100).
	 * @return The Date.
	 * @exception ParseException Thrown if the TJD start date cannot be parsed.
	 */
	protected static Date truncatedJulianDateSecondOfDayToDate(int tjd,int sod) throws ParseException
	{
		DateFormat dateFormat = null;
		Date date = null;
		TimeZone timeZone = null;
		int tjdFrom2003;
		long millis;

		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		// new code
		timeZone = TimeZone.getTimeZone("GMT+0");
		dateFormat.setTimeZone(timeZone);
		// set tjdStartDate to 1st Jan 2003 (TJD 12640)
		date = dateFormat.parse("2003-01-01T00:00:00");
		// get number of days from 1st Jan 2003 for tjd
		tjdFrom2003 = tjd-12640;
		// get number of millis from 1st Jan 2003 for tjd
		millis = ((long)tjdFrom2003)*86400000L; // 60*60*24*1000 = 86400000;
		// get number of millis from 1st Jan 1970 (Date EPOCH) for tjd
		millis = millis+date.getTime();
		// add sod to millis to get date millis from 1st Jan 1970
		// Note sod is in centoseconds
		millis = millis+(((long)sod)*10L);
		// set date time to this number of millis
		date.setTime(millis);
		return date;
	}
}
