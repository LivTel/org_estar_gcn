// GCNDatagramAlertData.java
// $Header: /home/cjm/cvs/org_estar_gcn/GCNDatagramAlertData.java,v 1.5 2012-06-22 14:29:27 cjm Exp $
package org.estar.gcn;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.estar.astrometry.*;

/**
 * Class holding data to do with a single GCN alert.
 * @author Chris Mottram
 * @version $Revision: 1.5 $
 */
public class GCNDatagramAlertData
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNDatagramAlertData.java,v 1.5 2012-06-22 14:29:27 cjm Exp $";
	/**
	 * Alert type.
	 */
	public final static int ALERT_TYPE_HETE = (1 << 0);
	/**
	 * Alert type.
	 */
	public final static int ALERT_TYPE_INTEGRAL = (1 << 1);
	/**
	 * Alert type.
	 */
	public final static int ALERT_TYPE_SWIFT = (1 << 2);
	/**
	 * Alert type.
	 */
	public final static int ALERT_TYPE_AGILE = (1 << 3);
	/**
	 * Alert type.
	 */
	public final static int ALERT_TYPE_FERMI = (1 << 4);
	/**
	 * Which spacecraft triggered this alert.
	 * @see #ALERT_TYPE_HETE
	 * @see #ALERT_TYPE_INTEGRAL
	 * @see #ALERT_TYPE_SWIFT
	 * @see #ALERT_TYPE_AGILE
	 * @see #ALERT_TYPE_FERMI
	 */
	protected int alertType = 0;
	/**
	 * The GCN trigger number.
	 */
	protected int triggerNumber = 0;
	/**
	 * The GCN sequence number (within a trigger).
	 */
	protected int sequenceNumber = 0;
	/**
	 * The right ascension of the alert in the specified epoch.
	 */
	protected RA ra = null;
	/**
	 * The declination of the alert in the specified epoch.
	 */
	protected Dec dec = null;
	/**
	 * The epoch of the ra and dec.
	 */
	protected double epoch = 2000.0;
	/**
	 * The date the GRB was detected.
	 */
	protected Date grbDate = null;
	/**
	 * The date the GRB notice was issued.
	 */
	protected Date noticeDate = null;
	/**
	 * The error box size (radius), in decimal arc-minutes.
	 */
	protected double errorBoxSize = 0.0;
	/**
	 * Error string.
	 */
	protected String errorString = null;
	/**
	 * Alert status bits. Can be filled with useful information to filter on.
	 * Currently only used for Swift BAT alerts where it is filled with solnStatus bits to filter on.
	 */
	protected int status = 0;
	/**
	 * Whether this alert is a real alert (false), or one generated for testing purposes (true).
	 */
	protected boolean test = false;
	/**
	 * Swift packets only, extra filtering can be done on the BAT merit parameters.
	 * For swift BAT alerts, hasMerit is true means the merit parameters indicate the burst IS a GRB.
	 * Only valid for Swift BAT alerts.
	 */
	protected boolean hasMerit = true;

	/**
	 * Default constructor.
	 */
	public GCNDatagramAlertData()
	{
		super();
	}

	/**
	 * Which spacecraft triggered this alert.
	 * @param i The alert type.
	 * @see #alertType
	 * @see #ALERT_TYPE_HETE
	 * @see #ALERT_TYPE_INTEGRAL
	 * @see #ALERT_TYPE_SWIFT
	 * @see #ALERT_TYPE_AGILE
	 * @see #ALERT_TYPE_FERMI
	 */
	public void setAlertType(int i)
	{
		alertType = i;
	}

	/**
	 * Return which spacecraft triggered this alert.
	 * @return The alert type.
	 * @see #alertType
	 * @see #ALERT_TYPE_HETE
	 * @see #ALERT_TYPE_INTEGRAL
	 * @see #ALERT_TYPE_SWIFT
	 * @see #ALERT_TYPE_AGILE
	 * @see #ALERT_TYPE_FERMI
	 */
	public int getAlertType()
	{
		return alertType;
	}

	/**
	 * Return which spacecraft triggered this alert.
	 * @return A string based on alert type - UNKNOWN if not known.
	 * @see #alertType
	 * @see #ALERT_TYPE_HETE
	 * @see #ALERT_TYPE_INTEGRAL
	 * @see #ALERT_TYPE_SWIFT
	 * @see #ALERT_TYPE_AGILE
	 * @see #ALERT_TYPE_FERMI
	 */
	public String getAlertTypeString()
	{
		switch(alertType)
		{
			case ALERT_TYPE_HETE:
				return "HETE";
			case ALERT_TYPE_INTEGRAL:
				return "INTEGRAL";
			case ALERT_TYPE_SWIFT:
				return "SWIFT";
			case ALERT_TYPE_AGILE:
				return "AGILE";
			case ALERT_TYPE_FERMI:
				return "FERMI";
			default:
				return "UNKNOWN";
		}
	}

	/**
	 * Set trigger number.
	 * @param n The number.
	 * @see #triggerNumber
	 */
	public void setTriggerNumber(int n)
	{
		triggerNumber = n;
	}

	/**
	 * Get trigger number.
	 * @return The number.
	 * @see #triggerNumber
	 */
	public int getTriggerNumber()
	{
		return triggerNumber;
	}

	/**
	 * Set sequence number.
	 * @param n The number.
	 * @see #sequenceNumber
	 */
	public void setSequenceNumber(int n)
	{
		sequenceNumber = n;
	}

	/**
	 * Get sequence number.
	 * @return The number.
	 * @see #sequenceNumber
	 */
	public int getSequenceNumber()
	{
		return sequenceNumber;
	}

	/**
	 * Set RA.
	 * @param r The J2000 right ascension.
	 * @see #ra
	 */
	public void setRA(RA r)
	{
		ra = r;
	}

	/**
	 * Get RA.
	 * @return The J2000 right ascension.
	 * @see #ra
	 */
	public RA getRA()
	{
		return ra;
	}

	/**
	 * Set declination.
	 * @param d The J2000 declination.
	 * @see #dec
	 */
	public void setDec(Dec d)
	{
		dec = d;
	}

	/**
	 * Get declination.
	 * @return The J2000 declination.
	 * @see #dec
	 */
	public Dec getDec()
	{
		return dec;
	}


	/**
	 * Set epoch of the ra and dec, in decimal years.
	 * @param y The epoch of the ra and dec, in decimal years.
	 * @see #epoch
	 */
	public void setEpoch(double y)
	{
		epoch = y;
	}

	/**
	 * Set epoch of the ra and dec, in decimal years.
	 * @param d The epoch of the ra and dec, as a date, to be converted into decimal years.
	 * @see #epoch
	 */
	public void setEpoch(Date d) throws ParseException
	{
         	GregorianCalendar calendar = null;
         	GregorianCalendar startOfYearCalendar = null;
		DateFormat dateFormat = null;
		//Date startOfYearDate = null;
		double dMillis,millisInYear;

		calendar = new GregorianCalendar();
		calendar.setTime(d);
		//dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		//startOfYearDate = dateFormat.parse(""+calendar.get(Calendar.YEAR)+"-01-01T00:00:00");
		startOfYearCalendar = new GregorianCalendar();
		startOfYearCalendar.setTime(d);
		startOfYearCalendar.set(Calendar.DAY_OF_YEAR,1);
		startOfYearCalendar.set(Calendar.HOUR,0);
		startOfYearCalendar.set(Calendar.MINUTE,0);
		startOfYearCalendar.set(Calendar.SECOND,0);
		dMillis = d.getTime()-startOfYearCalendar.getTime().getTime();
		if(calendar.isLeapYear(calendar.get(Calendar.YEAR)))
		    millisInYear = 366.0*24.0*60.0*60.0*1000.0;
		else
		    millisInYear = 365.0*24.0*60.0*60.0*1000.0;
		epoch = calendar.get(Calendar.YEAR)+(dMillis/millisInYear);
	}

	/**
	 * Get epoch of the ra and dec, in decimal years.
	 * @return The epoch of the ra and dec, in decimal years.
	 * @see #epoch
	 */
	public double getEpoch()
	{
		return epoch;
	}

	/**
	 * Set date GRB was detected.
	 * @param d The date.
	 * @see #grbDate
	 */
	public void setGRBDate(Date d)
	{
		grbDate = d;
	}

	/**
	 * Get date GRB was detected.
	 * @return The date.
	 * @see #grbDate
	 */
	public Date getGRBDate()
	{
		return grbDate;
	}

	/**
	 * Set date GRB notice was sent out.
	 * @param d The date.
	 * @see #noticeDate
	 */
	public void setNoticeDate(Date d)
	{
		noticeDate = d;
	}

	/**
	 * Get date GRB notice was sent out.
	 * @return The date.
	 * @see #noticeDate
	 */
	public Date getNoticeDate()
	{
		return noticeDate;
	}

	/**
	 * Set size of error box (radius), in decimal arc-minutes.
	 * @param s The size of error box (radius), in decimal arc-minutes.
	 * @see #errorBoxSize
	 */
	public void setErrorBoxSize(double s)
	{
		errorBoxSize = s;
	}

	/**
	 * Get size of error box (radius), in decimal arc-minutes.
	 * @return The size of error box (radius), in decimal arc-minutes.
	 * @see #errorBoxSize
	 */
	public double getErrorBoxSize()
	{
		return errorBoxSize;
	}

	public void setError(String s)
	{
		errorString = s;
	}

	/**
	 * Set status number.
	 * Currently only used for Swift BAT alerts where it is filled with solnStatus bits to filter on.
	 * @param s The status number.
	 * @see #status
	 */
	public void setStatus(int s)
	{
		status = s;
	}

	/**
	 * Get status number.
	 * Currently only used for Swift BAT alerts where it is filled with solnStatus bits to filter on.
	 * @return The status number.
	 * @see #status
	 */
	public int getStatus()
	{
		return status;
	}

	/**
	 * Set whether this alert is a real alert or a test.
	 * @param b Should be false if this is a real alert, true if this is a test alert.
	 * @see #test
	 */
	public void setTest(boolean b)
	{
		test = b;
	}

	/**
	 * Get whether this alert is real, or a test one.
	 * @return true is this is a test alert, else false.
	 * @see #test
	 */
	public boolean getTest()
	{
		return test;
	}

	/**
	 * Set whether this alert has merit or not.
	 * Only valid for Swift BAT alerts.
	 * @param b Should be false if this is not a GRB, true if it is a GRB.
	 * @see #hasMerit
	 */
	public void setHasMerit(boolean b)
	{
		hasMerit = b;
	}

	/**
	 * Get whether this alert has merit.
	 * Only valid for Swift BAT alerts.
	 * @return true is this is a real GRB, false if it is not.
	 * @see #hasMerit
	 */
	public boolean getHasMerit()
	{
		return hasMerit;
	}

	public String toString()
	{
		return toString("");
	}

	public String toString(String prefix)
	{
		StringBuffer sb = null;

		sb = new StringBuffer();
		sb.append(prefix+"Trigger "+triggerNumber+" Seq "+sequenceNumber+"\n");
		if(noticeDate != null)
			sb.append(prefix+"Notice Date "+noticeDate+"\n");
		if(grbDate != null)
			sb.append(prefix+"GRB Date "+grbDate+"\n");
		sb.append(prefix+"RA "+ra+"\n");
		sb.append(prefix+"Dec "+dec+"\n");
		sb.append(prefix+"Epoch "+epoch+"\n");
		sb.append(prefix+"Error Box "+errorBoxSize+" arcmin radius.\n");
		if(errorString != null)
			sb.append(prefix+"Error String"+errorString+"\n");
		return sb.toString();
	}
}
//
// $Log: not supported by cvs2svn $
// Revision 1.4  2008/03/17 19:20:43  cjm
// Added hasMerit parameter, for filtering Swift BAT alerts on Merit Parameters.
//
// Revision 1.3  2005/03/01 18:51:07  cjm
// Added test boolean.
//
// Revision 1.2  2005/02/17 17:35:57  cjm
// Added status variable.
//
// Revision 1.1  2004/10/19 17:10:06  cjm
// Initial revision
//
// Revision 1.1  2003/05/19 15:47:04  cjm
// Initial revision
//
//
