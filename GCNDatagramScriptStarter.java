// GCNDatagramScriptStarter.java
package org.estar.gcn;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.estar.astrometry.*;

/**
 * This class is a Runnable, that sits on a MulticastSocket, waiting to be sent packets from
 * a program sitting on a GCN Bacodine socket. This program is sent a copy
 * of the alert packets in a data packet.
 * The class runs a script if  the packet contains an alert it wants to respond to.
 * The script is started with parameters as follows:
 * <pre>
 * -ra  <ra> -dec <dec> -epoch <epoch> -error_box <error_box> -trigger_number <tnum> -sequence_number <snum> -grb_date <date> -notice_date <date>
 * </pre>
 * Note the &lt;error_box&gt; is the radius in arc-minutes.
 * @author Chris Mottram
 * @version $Revision: 1.5 $
 */
public class GCNDatagramScriptStarter implements Runnable
{
// constants
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNDatagramScriptStarter.java,v 1.5 2005-01-20 15:35:55 cjm Exp $";
	/**
	 * The default port to listen on, as agreed by Steve.
	 */
	public final static int DEFAULT_PORT = 2005;
	/**
	 * Default group address. (224.g.r.b).
	 */
	public final static String DEFAULT_GROUP_ADDRESS = "224.103.114.98";
	public final static int PACKET_LENGTH = 160;
	protected boolean quit = false;
	protected MulticastSocket socket = null;
	protected DatagramPacket packet = null;
	protected DataInputStream inputStream = null;
	/** 
	 *The InetAddress of the Multicast channel to listen to.
	 */
	protected InetAddress groupAddress = null;
	/** 
	 * The port to attach to.
	 */
	protected int port = DEFAULT_PORT;
	protected byte packetBuff[];
	protected GCNDatagramAlertData alertData = null;
	/**
	 * Logger to log to.
	 */
	protected GCNDatagramScriptStarterLogger logger = null;
	/**
	 * The name of the script/program to call.
	 */
	protected String script = null;
	/**
	 * The maximum error box (radius) in arcseconds, alerts with error boxs less than this size call the script.
	 */
	protected double maxErrorBox = 60*60;

	/**
	 * Which alerts are passed on to the script.
	 * @see GCNDatagramAlertData#ALERT_TYPE_HETE
	 * @see GCNDatagramAlertData#ALERT_TYPE_INTEGRAL
	 * @see GCNDatagramAlertData#ALERT_TYPE_SWIFT
	 */
	protected int allowedAlerts = 0;

	/**
	 * Default constructor. Initialises groupAddress to default.
	 * @exception UnknownHostException Thrown if the default address is unknown
	 * @see #groupAddress
	 * @see #DEFAULT_GROUP_ADDRESS
	 */
	public GCNDatagramScriptStarter() throws UnknownHostException
	{
		super();
		groupAddress = InetAddress.getByName(DEFAULT_GROUP_ADDRESS);
	}

	/**
	 * Run method.
	 * @see #quit
	 * @see #initSocket
	 * @see #receivePacket
	 * @see #processData
	 * @see #alertFilter
	 * @see #startScript
	 */
	public void run()
	{
		try
		{
			if(logger != null)
				logger.log(this.getClass().getName()+":run:Started.");
			quit = false;
			initSocket();
			while(quit == false)
			{
				receivePacket();
				processData();
				if(alertFilter())
					startScript();
			}
		}
		catch(Exception e)
		{
			if(logger != null)
				logger.error(this.getClass().getName()+":run:",e);
			else
			{
				System.err.println(this.getClass().getName()+":run:An error occured:"+e);
				e.printStackTrace(System.err);
			}
		}
	}

	/**
	 * Quit the thread.
	 * @see #quit
	 */
	public void quit()
	{
		quit = true;
	}

	public void setPort(int p)
	{
		port = p;
	}

	public void setGroupAddress(InetAddress i)
	{
		groupAddress = i;
	}

	/**
	 * Method to set script to run.
	 * @param s The name of the script.
	 * @see #script
	 */
	public void setScript(String s)
	{
		script = s;
	}

	/**
	 * Method to set alerts that will call script.
	 * @param i A bitwise int of the alerts to allow.
	 * @see #allowedAlerts
	 * @see GCNDatagramAlertData#ALERT_TYPE_HETE
	 * @see GCNDatagramAlertData#ALERT_TYPE_INTEGRAL
	 * @see GCNDatagramAlertData#ALERT_TYPE_SWIFT
	 */	
	public void setAllowedAlerts(int i)
	{
		allowedAlerts = i;
	}

	/**
	 * Method to add to the set of alerts that will call script.
	 * @param i A bitwise int of the alerts to add.
	 * @see #allowedAlerts
	 * @see GCNDatagramAlertData#ALERT_TYPE_HETE
	 * @see GCNDatagramAlertData#ALERT_TYPE_INTEGRAL
	 * @see GCNDatagramAlertData#ALERT_TYPE_SWIFT
	 */	
	public void addAllowedAlerts(int i)
	{
		allowedAlerts |= i;
	}


	/**
	 * Method to set the maximum error box (radius) in arcseconds of an alert to run the script with.
	 * Alerts with error boxs less than this size call the script.
	 * @param d The maximum error box (radius) in arcseconds.
	 * @see #maxErrorBox
	 */
	public void setMaxErrorBox(double d)
	{
		maxErrorBox = d;
	}

	// protected methods.
	/**
	 * Initialsie connection.
	 * @see #port
	 * @see #socket
	 * @see #groupAddress
	 */
	protected void initSocket() throws Exception
	{
		logger.log(this.getClass().getName()+":initSocket:port = "+port+" Group Address: "+
				  groupAddress);
		socket = new MulticastSocket(port);
		socket.joinGroup(groupAddress);
	}

	/**
	 * Receive packet.
	 * @see #packetBuff
	 * @see #PACKET_LENGTH
	 * @see #packet
	 * @see #socket
	 */
	protected void receivePacket() throws Exception
	{
		logger.log(this.getClass().getName()+":receivePacket:Started.");
		packetBuff = new byte[PACKET_LENGTH];
		packet = new DatagramPacket(packetBuff,packetBuff.length);
		logger.log(this.getClass().getName()+":receivePacket:Awaiting packet.");
		socket.receive(packet);
		logger.log(this.getClass().getName()+":receivePacket:Packet received.");
	}

	/**
	 * Process data in packet
	 * @see #packet
	 * @see #inputStream
	 * @see #alertData
	 */
	protected void processData() throws Exception
	{
		ByteArrayInputStream bin = null;
		byte buff[];

		logger.log(this.getClass().getName()+":processData:Started.");
		buff = packet.getData();
		// Create an input stream from the buffer.
		bin = new ByteArrayInputStream(buff, 0,buff.length);
		inputStream = new DataInputStream(bin);
		alertData = new GCNDatagramAlertData();
		// Set notice date to now. Note this should really be set to pkt_sod,
		// but this won't work if the notice is sent around midnight.
		alertData.setNoticeDate(new Date());
		// parse data
		// call any listeners with parsed data
		int type = readType();
		logger.log("Read packet type: "+type);
		switch (type)
		{
		    case 3: 
			logger.log(" [IMALIVE]");
			readImalive();
			break;
		    case 4:
			logger.log(" [KILL]");
			break;
		    case 34:  
			logger.log(" [SAX/WFC_GRB_POS]");
			readSax();
			break;
		    case 40: 
			logger.log(" [HETE_ALERT]");// Note no position
			readHeteAlert();
			break;
		    case 41:
			logger.log(" [HETE_UPDATE]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_HETE);
			readHeteUpdate();
			break; 
		    case 51:
			logger.log(" [INTEGRAL_POINTDIR]");
			readIntegralPointing();
			break;
		    case 53:
			logger.log(" [INTEGRAL_WAKEUP]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_INTEGRAL);
			readIntegralWakeup();
			break;
		    case 54:
			logger.log(" [INTEGRAL_REFINED]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_INTEGRAL);
			readIntegralRefined();
			break;
		    case 55:
			logger.log(" [INTEGRAL_OFFLINE]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_INTEGRAL);
			readIntegralOffline();
			break;
		    case 60: // Note no position
			logger.log(" [SWIFT_BAT_GRB_ALERT]");
			readSwiftBatAlert();
			break;
		    case 61:
			logger.log(" [SWIFT_BAT_GRB_POSITION]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_SWIFT);
			readSwiftBatGRBPosition();
			break;
		    case 62: // Note no position
			logger.log(" [SWIFT_BAT_GRB_NACK_POSITION]");
			break;
		    case 65: // Note no (useful) position
			logger.log(" [SWIFT_FOM_OBS]");
			break;
		    case 66: // Note no (useful) position
			logger.log(" [SWIFT_SC_SLEW]");
			break;
		    case 67:
			logger.log(" [SWIFT_XRT_POSITION]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_SWIFT);
			readSwiftXrtGRBPosition();
			break;
		    case 71: // Note no position
			logger.log(" [SWIFT_XRT_NACK_POSITION]");
			break;
		    case 81:
			logger.log(" [SWIFT_UVOT_POSITION]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_SWIFT);
			readSwiftUvotGRBPosition();
			break;
		    case 82:
			logger.log(" [SWIFT_BAT_GRB_POS_TEST]");
			//alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_SWIFT);
			//readSwiftTestGRBPosition();
			break;
		    default:
			logger.log(" [TYPE-"+type+"]");
		}
	}

	/**
	 * Method to filter which alerts will call the script.
	 * Note maxErrorBox is a radius in arc-seconds, whereas alert data contains error box radius's in arc-minutes.
	 * @return true if the script should be called, false if it shouldn't.
	 * @see #alertData
	 * @see #allowedAlerts
	 * @see #maxErrorBox
	 */
	protected boolean alertFilter()
	{
		if((allowedAlerts & alertData.getAlertType()) == 0)
		{
			logger.log("alertFilter stopped propogation of alert on type: allowed alerts "+allowedAlerts+
				   " not compatible with alertData alert type "+alertData.getAlertType()+".");
			return false;
		}
		// Note maxErrorBox is a radius in arc-seconds, 
		// whereas alert data contains error box radius's in arc-minutes.
		if(maxErrorBox < (alertData.getErrorBoxSize()*60.0))
		{
			logger.log("alertFilter stopped propogation of alert on error box: max error box radius "+
				   maxErrorBox+" arcseconds smaller than alert error box radius "+
				   (alertData.getErrorBoxSize()*60.0)+" arcseconds.");
			return false;
		}
		return true;
	}

	/**
	 * Method to call the script. The script is started with parameters as follows:
	 * <pre>
	 * -ra  <ra> -dec <dec> -epoch <epoch> -error_box <error_box> -trigger_number <tnum> -sequence_number <snum> -grb_date <date> -notice_date <date>
	 * </pre>
	 * Note the &lt;error_box&gt; is the radius in arc-minutes.
	 * A script thread is started to monitor the spawned script process.
	 * @see #script
	 * @see #alertData
	 */
	protected void startScript() throws Exception
	{
		Runtime rt = null;
		DateFormat dateFormat = null;
		StringBuffer execString = null;
		ScriptThread scriptThread = null;
		Thread thread = null;
		Process process = null;

		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		rt = Runtime.getRuntime();
		execString = new StringBuffer();
		execString.append(script+" -"+alertData.getAlertTypeString()+
					" -ra "+alertData.getRA()+
					" -dec "+alertData.getDec()+
					" -epoch "+alertData.getEpoch()+
					" -error_box "+alertData.getErrorBoxSize()+
					" -trigger_number "+alertData.getTriggerNumber()+
					" -sequence_number "+alertData.getSequenceNumber());
		if(alertData.getGRBDate() != null)
			execString.append(" -grb_date "+dateFormat.format(alertData.getGRBDate()));
		if(alertData.getNoticeDate() != null)
			execString.append(" -notice_date "+dateFormat.format(alertData.getNoticeDate()));
		logger.log("startScript: Executing:"+execString.toString());
		process = rt.exec(execString.toString());
		scriptThread = new ScriptThread(process);
		thread = new Thread(scriptThread);
		thread.start();
	}

	protected int readType() throws IOException
	{
		int type = inputStream.readInt();	
		return type;
	}
    
	protected void readTerm() throws IOException
	{
		inputStream.readByte();
		inputStream.readByte();
		inputStream.readByte();
		inputStream.readByte();
		logger.log("-----Terminator");
	}
    
    /** Read the header. */
	protected void readHdr() throws IOException
	{
		int seq  = inputStream.readInt(); // SEQ_NO.
		int hop  = inputStream.readInt(); // HOP_CNT. 
		logger.log("Header: Packet Seq.No: "+seq+" Hop Count: "+hop);
	}
    
	/** 
	 * Read the SOD for date.
	 */
	protected void readSod() throws IOException
	{
		int sod = inputStream.readInt();
		logger.log("SOD: "+sod);
	}
    
	/** 
	 * Read stuffing bytes. 
	 */
	protected void readStuff(int from, int to) throws IOException
	{
		for (int i = from; i <= to; i++)
		{
			inputStream.readInt();
		}
		logger.log("Skipped: "+from+" to "+to);
	}

	/**
	 * IAMALIVE packets.
	 */
	public void readImalive()
	{
		try
		{
			readHdr();
			readSod();
			readStuff(4, 38);
			readTerm();
		}
		catch (IOException e)
		{
			logger.error("IM_ALIVE: Error reading: ",e);
		}
	}
    
	public void readSax()
	{
		try
		{
			readHdr(); // 0, 1, 2
			readSod();     // 3
			readStuff(4,4);   // 4 - spare
			int burst_tjd = inputStream.readInt(); // 5 - burst_tjd
			int burst_sod = inputStream.readInt(); // 6 - burst_sod
			logger.log("Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
			int bra =  inputStream.readInt(); // 7 - burst RA [ x10000 degrees]
			int bdec = inputStream.readInt(); // 8 - burst Dec [x10000 degrees].
			int bint = inputStream.readInt(); // 9 - burst intens mCrab.
			logger.log("RA: "+bra+" Dec: "+bdec+" Intensity:"+bint+" [mcrab]");
			readStuff(10, 10);   // 10 - spare
			int berr  = inputStream.readInt(); // 11 - burst error
			int bconf = inputStream.readInt(); // 12 - burst conf [% x 100].
			logger.log("Burst Error: "+berr+" Confidence: "+bconf);
			readStuff(13, 17); // 13,, 17 - spare.		
			int trig_id = inputStream.readInt(); // 18 - trigger flags.
			logger.log("Trigger Flags: "+trig_id);
			inputStream.readInt(); // 19 - stuff.
			readStuff(20, 38); // 20,, 38 - spare.
			readTerm(); // 39 - TERM.	   
		}
		catch (IOException e)
		{
			logger.error("SAX_WFC_POS: Error reading: ",e);
		}
	}
    
	public void readHeteAlert()
	{
		try
		{
			readHdr(); // 0, 1, 2
			readSod();     // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int burst_tjd = inputStream.readInt(); // 5 - burst_tjd
			int burst_sod = inputStream.readInt(); // 6 - burst_sod
			logger.log("Trig. Seq. No: "+tsn+" Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
			readStuff(7, 8); // 7, 8 - spare 
			//int trig_flags = GAMMA_TRIG | WXM_TRIG | PROB_GRB;
			int trig_flags = inputStream.readInt(); // 9 - trig_flags
			logger.log("Trigger Flags: "+trig_flags);
			int gamma = inputStream.readInt();   // 10 - gamma_cnts
			int wxm = inputStream.readInt(); // 11 - wxm_cnts
			int sxc = inputStream.readInt();  // 12 - sxc_cnts
			logger.log("Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
			int gammatime = inputStream.readInt(); // 13 - gamma_time
			int wxmtime = inputStream.readInt(); // 14 - wxm_time
			int scpoint = inputStream.readInt(); // 15 - sc_point
			logger.log("Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
			logger.log("SC Point:"+scpoint);
			readStuff(16, 38); // 16,, 38 spare
			readTerm(); // 39 - TERM.

		}
		catch  (IOException e)
		{
			logger.error("HETE ALERT:readHeteAlert: ",e);
		}
	}

	/**
	 * HETE_S/C_UPDATE (TYPE=41).
	 */
	public void readHeteUpdate()
	{ 
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;
		int bra = 0;
		int bdec = 0;
		int trigNum = 0;
		int mesgNum = 0;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod();     // 3 - pkt_sod
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			trigNum = (tsn & 0x0000FFFF);
			mesgNum = (tsn & 0xFFFF0000) >> 16;
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			int burstTjd = inputStream.readInt(); // 5 - burst_tjd
			int burstSod = inputStream.readInt(); // 6 - burst_sod
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			logger.log("Burst: TJD:"+burstTjd+" SOD: "+burstSod);
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			bra = inputStream.readInt(); // Burst RA (x10e4 degs). // 7 - burst_ra
			bdec = inputStream.readInt(); // Burst Dec (x10e4 degs). // 8 = burst_dec
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians((double)bra)/10000.0);
			dec.fromRadians(Math.toRadians((double)bdec)/10000.0);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+burstDate);
			int trig_flags = inputStream.readInt(); // 9 - trig_flags
			logger.log("Trigger Flags: 0x"+Integer.toHexString(trig_flags));
			int gamma = inputStream.readInt();   // 10 - gamma_cnts
			int wxm   = inputStream.readInt(); // 11 - wxm_cnts
			int sxc   = inputStream.readInt();  // 12 - sxc_cnts
			logger.log("Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
			int gammatime = inputStream.readInt(); // 13 - gamma_time
			int wxmtime = inputStream.readInt(); // 14 - wxm_time
			int scpoint = inputStream.readInt(); // 15 - sc_point
			int sczra   = (scpoint & 0xFFFF0000) >> 16;
			int sczdec  = (scpoint & 0x0000FFFF);
			logger.log("Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
			logger.log("SC Pointing: RA(deg): "+(((double)sczra)/10000.0)+
					   " Dec(deg): "+(((double)sczdec)/10000.0));
			int wxra1 = inputStream.readInt();  // 16 - WXM ra1 (x10e4 degs).
			int wxdec1 = inputStream.readInt(); // 17 WXM dec1 (x10e4 degs).
			int wxra2 = inputStream.readInt();  // 18 - WXM ra2 (x10e4 degs).
			int wxdec2 = inputStream.readInt(); // 19 WXM dec2 (x10e4 degs).
			int wxra3 = inputStream.readInt();  // 20 - WXM ra3 (x10e4 degs).
			int wxdec3 = inputStream.readInt(); // 21 WXM dec3 (x10e4 degs).
			int wxra4 = inputStream.readInt();  // 22 - WXM ra4 (x10e4 degs).
			int wxdec4 = inputStream.readInt(); // 23 WXM dec4 (x10e4 degs).
			int wxErrors = inputStream.readInt(); // 24 WXM Errors (bit-field) - Sys & Stat.
			// wxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits.
			logger.log("WXM error box (radius,arcsec) : statistical : "+((wxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(wxErrors&0x0000FFFF)+".");
			int wxDimSig = inputStream.readInt(); // 25 WXM Packed numbers.
			// wxDimSig contains the maximum dimension of the WXM error box [units arcsec] in top 16 bits
			int wxErrorBoxArcsec = (wxDimSig&0xFFFF0000)>>16;
			logger.log("WXM error box (diameter,arcsec) : "+wxErrorBoxArcsec+".");
			int sxra1 = inputStream.readInt();  // 26 - SC ra1 (x10e4 degs).
			int sxdec1 = inputStream.readInt(); // 27 SC dec1 (x10e4 degs).
			int sxra2 = inputStream.readInt();  // 28 - SC ra2 (x10e4 degs).
			int sxdec2 = inputStream.readInt(); // 29 SC dec2 (x10e4 degs).
			int sxra3 = inputStream.readInt();  // 30 - SC ra3 (x10e4 degs).
			int sxdec3 = inputStream.readInt(); // 31 SC dec3 (x10e4 degs).
			int sxra4 = inputStream.readInt();  // 32 - SC ra4 (x10e4 degs).
			int sxdec4 = inputStream.readInt(); // 33 SC dec4 (x10e4 degs).
			int sxErrors = inputStream.readInt(); // 34 SC Errors (bit-field) - Sys & Stat.
			// sxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits).
			logger.log("SXC error box (radius,arcsec) : statistical : "+((sxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(sxErrors&0x0000FFFF)+".");
			int sxDimSig = inputStream.readInt(); // 35 SC Packed numbers.
			// sxDimSig contains the maximum dimension of the SXC error box [units arcsec] in top 16 bits
			int sxErrorBoxArcsec = (sxDimSig&0xFFFF0000)>>16;
			logger.log("SXC error box (diameter,arcsec) : "+sxErrorBoxArcsec+".");
			alertData.setErrorBoxSize(((double)(Math.max(wxErrorBoxArcsec,sxErrorBoxArcsec)))/
						  (2.0*60.0));// radius, in arc-min
			int posFlags = inputStream.readInt(); // 36 - pos_flags
			logger.log("Pos Flags: 0x"+Integer.toHexString(posFlags));
			int validity = inputStream.readInt(); // 37 - validity flags.
			logger.log("Validity Flag: 0x"+Integer.toHexString(validity));
			if(validity == 0x00000001)
			{
				alertData.setRA(ra);
				alertData.setDec(dec);
				// epoch is "current", is this burst date or notice date?
				alertData.setEpoch(burstDate);
			}
			else
				logger.log("BURST INVALID:RA/Dec not set.");
			readStuff(38, 38); // 38 -spare
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("HETE UPDATE: Error reading: ",e);
		}
	}

	public void readIntegralPointing()
	{
		RA ra = null;
		Dec dec = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			int slewTjd = inputStream.readInt(); // 5 Slew TJD.
			int slewSod = inputStream.readInt(); // 6 Slew SOD.
			logger.log("Slew at: "+slewTjd+" TJD Time: "+slewSod+" Sod.");
			readStuff(7, 11);
			int flags   =  inputStream.readInt(); // 12 Test Flags.
			logger.log("Test Flags: ["+Integer.toHexString(flags).toUpperCase()+"]");
			inputStream.readInt(); // 13 spare.
			int scRA    = inputStream.readInt(); // 14 Next RA *10000.
			int scDec   = inputStream.readInt(); // 15 Next Dec *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians((double)scRA)/10000.0);
			dec.fromRadians(Math.toRadians((double)scDec)/10000.0);
			logger.log("SC Slew to RA: "+ra+" Dec:"+dec);
			readStuff(16,18);
			int scStat  = inputStream.readInt(); // 19 Status and attitude flags.
			logger.log("Status Flags;: ["+Integer.toHexString(scStat).toUpperCase()+"]");
			readStuff(20, 38);
			readTerm(); // 39 - TERM.	 
		}
		catch  (Exception e)
		{
			logger.error("INTEGRAL POINTING: Error reading: ",e);
		}
	}

	/**
	 * Integral Wakeup (TYPE 53).
	 */
	public void readIntegralWakeup()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// Note the burst data is in apparent coordinates (current EPOCH)
			alertData.setRA(ra);
			alertData.setDec(dec);
			// epoch is "current", is this burst date or notice date?
			alertData.setEpoch(burstDate);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+alertData.getEpoch());
			int detFlags   =  inputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  inputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  inputStream.readInt(); // 11 burst error (arcsec)
			// burstError is radius of circle (arcsecs) that contains TBD% c.l.  of bursts
			alertData.setErrorBoxSize((((double)burstError)/60.0));// in arc-min
			int testMpos = inputStream.readInt(); // 12 Test/Multi-Position flags.
			logger.log("Status Flags: [0x"+Integer.toHexString(testMpos).toUpperCase()+"]");
			logger.log("testMpos 0x"+Integer.toHexString(testMpos).toUpperCase()+
				   " & 0x"+Integer.toHexString((1<<31)).toUpperCase()+" = "+((testMpos & (1<<31))>1));
			if((testMpos & (1<<31))>1)
			{
				logger.log("Test Notice - Not a real event.");
				alertData.setAlertType(0); // ensure test notice not propogated as an alert.
			}
			logger.log("Burst error: "+((double)burstError)+" arcsec radius.");
			readStuff(13, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.	 
		}
		catch  (Exception e)
		{
			logger.error("INTEGRAL Wakeup: Error reading: ",e);
		}
	}

	/**
	 * Integral Refined (TYPE 54).
	 */
	public void readIntegralRefined()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// Note the burst data is in apparent coordinates (current EPOCH)
			alertData.setRA(ra);
			alertData.setDec(dec);
			// epoch is "current", is this burst date or notice date?
			alertData.setEpoch(burstDate);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+alertData.getEpoch());
			int detFlags   =  inputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  inputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  inputStream.readInt(); // 11 burst error (arcsec)
			// burstError is radius of circle (arcsecs) that contains TBD% c.l.  of bursts
			logger.log("Burst error: "+((double)burstError)+" arcsec radius.");
			alertData.setErrorBoxSize((((double)burstError)/60.0));// in arc-min
			int testMpos = inputStream.readInt(); // 12 Test/Multi-Position flags.
			logger.log("Status Flags: [0x"+Integer.toHexString(testMpos).toUpperCase()+"]");
			logger.log("testMpos 0x"+Integer.toHexString(testMpos).toUpperCase()+
				   " & 0x"+Integer.toHexString((1<<31)).toUpperCase()+" = "+((testMpos & (1<<31))>1));
			if((testMpos & (1<<31))>1)
			{
				logger.log("Test Notice - Not a real event.");
				alertData.setAlertType(0); // ensure test notice not propogated as an alert.
			}
			readStuff(13, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.	 
		}
		catch  (Exception e)
		{
			logger.error("INTEGRAL Refined: Error reading: ",e);
		}
	}

	public void readIntegralOffline()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// Note the burst data is in apparent coordinates (current EPOCH)
			alertData.setRA(ra);
			alertData.setDec(dec);
			// epoch is "current", is this burst date or notice date?
			alertData.setEpoch(burstDate);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+alertData.getEpoch());
			int detFlags   =  inputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  inputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			// burstError is radius of circle (arcsecs) that contains TBD% c.l.  of bursts
			int burstError   =  inputStream.readInt(); // 11 burst error (arcsec)
			logger.log("Burst error: "+((double)burstError)+" arcsec radius.");
			alertData.setErrorBoxSize((((double)burstError)/60.0));// in arc-min
			int testMpos = inputStream.readInt(); // 12 Test/Multi-Position flags.
			logger.log("Status Flags: [0x"+Integer.toHexString(testMpos).toUpperCase()+"]");
			logger.log("testMpos 0x"+Integer.toHexString(testMpos).toUpperCase()+
				   " & 0x"+Integer.toHexString((1<<31)).toUpperCase()+" = "+((testMpos & (1<<31))>1));
			if((testMpos & (1<<31))>1)
			{
				logger.log("Test Notice - Not a real event.");
				alertData.setAlertType(0); // ensure test notice not propogated as an alert.
			}
			readStuff(13, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("INTEGRAL Offline: Error reading: ",e);
		}
	}

	/**
	 * Swift BAT alert (Type 60).
	 */
	public void readSwiftBatAlert()
	{
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			readStuff(7, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT BAT Alert: Error reading: ",e);
		}
	}

	/**
	 * Swift BAT position (Type 61,SWIFT_BAT_GRB_POSITION).
	 */
	public void readSwiftBatGRBPosition()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// The BAT returns J2000 coordinates.
			alertData.setRA(ra);
			alertData.setDec(dec);
			alertData.setEpoch(2000.0);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+2000.0);
			int burstFlue = inputStream.readInt(); // 9 Burst flue (counts) number of events.
			int burstIPeak = inputStream.readInt(); // 10 Burst ipeak (counts*ff) counts.
			int burstError = inputStream.readInt(); // 11 Burst error degrees (0..180) * 10000)
			// burst error is radius of circle in degrees*10000 containing TBD% of bursts!
			// Initially, hardwired to 4 arcmin (0.067 deg) radius.
			alertData.setErrorBoxSize((((double)burstError)*60.0)/10000.0);// in arc-min
			readStuff(12, 17);// Phi, theta, integ_time, spare x 2
			int solnStatus = inputStream.readInt(); // 18 Type of source found (bitfield)
			if((solnStatus & (1<<0))>0)
				logger.log("Soln Status : A point source was found.");
			if((solnStatus & (1<<1))>0)
				logger.log("Soln Status : It is a GRB.");
			else
				logger.log("Soln Status : It is NOT a GRB?!.");
			if((solnStatus & (1<<2))>0)
				logger.log("Soln Status : It is an interesting source.");
			if((solnStatus & (1<<3))>0)
				logger.log("Soln Status : It is a catalogue source.");
			if((solnStatus & (1<<4))>0)
				logger.log("Soln Status : It is an image trigger.");
			else
				logger.log("Soln Status : It is a rate trigger.");
			readStuff(19, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT BAT GRB POSITION: Error reading: ",e);
		}
	}

	/**
	 * Swift XRT position (Type 67,SWIFT_GRB_XRT_POSITION).
	 */
	public void readSwiftXrtGRBPosition()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// The BAT returns J2000 coordinates.
			alertData.setRA(ra);
			alertData.setDec(dec);
			alertData.setEpoch(2000.0);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+2000.0);
			int burstFlux = inputStream.readInt(); // 9 Burst flux (counts) number of events.
			readStuff(10, 10); // 10 spare.
			int burstError = inputStream.readInt(); // 11 Burst error degrees (0..180) * 10000.
			// burst error is radius of circle in degrees*10000 containing 90% of bursts.
			// Initially, hardwired to 9".
			alertData.setErrorBoxSize((((double)burstError)*60.0)/10000.0);// in arc-min
			readStuff(12, 38);// X_TAM, Amp_Wave, misc, det_sig plus lots of spares.
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT XRT GRB POS: Error reading: ",e);
		}
	}

	/**
	 * Swift XRT position (Type 81,SWIFT_UVOT_POSITION).
	 */
	public void readSwiftUvotGRBPosition()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = inputStream.readInt();   // 4 - trig_obs_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// The UVOT returns J2000 coordinates.
			alertData.setRA(ra);
			alertData.setDec(dec);
			alertData.setEpoch(2000.0);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
			logger.log("Epoch: "+2000.0);
			int burstMag = inputStream.readInt(); // 9 Uvot mag * 100
			readStuff(10, 10); // 10 filter integer.
			int burstError = inputStream.readInt(); // 11 Burst error in centi-degrees (0..180.0)*10000.
			// burst error is radius of circle in degrees*10000 containing 90% of bursts.
			// Initially, hardwired to 9".
			alertData.setErrorBoxSize((((double)burstError)*60.0)/10000.0);// in arc-min
			readStuff(12, 38);// misc plus lots of spares.
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT UVOT GRB POS: Error reading: ",e);
		}
	}


	/**
	 * Return a Java Date for the specified input fields.
	 * @param tjd Truncated Julian Date, TJD=12640 is 01 Jan 2003.
	 * @param sod Actually centi-seconds in the day, (seconds * 100).
	 * @return The Date.
	 * @exception ParseException Thrown if the TJD start date cannot be parsed.
	 */
	protected Date truncatedJulianDateSecondOfDayToDate(int tjd,int sod) throws ParseException
	{
		DateFormat dateFormat = null;
		Date date = null;
		int tjdFrom2003;
		long millis;

		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
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

	/**
	 * Argument parser.
	 * @param args The argument list.
	 * @see #setPort
	 * @see #setGroupAddress
	 * @see #setScript
	 * @see #setMaxErrorBox
	 * @see #setAllowedAlerts
	 * @see #addAllowedAlerts
	 * @see GCNDatagramAlertData#ALERT_TYPE_HETE
	 * @see GCNDatagramAlertData#ALERT_TYPE_INTEGRAL
	 * @see GCNDatagramAlertData#ALERT_TYPE_SWIFT
	 */
	protected void parseArgs(String args[])
	{
		int intValue;
		double doubleValue;

		// parse arguments
		setAllowedAlerts(0);
		for(int i = 0; i < args.length; i++)
		{

			if(args[i].equals("-all"))
			{
				addAllowedAlerts(GCNDatagramAlertData.ALERT_TYPE_HETE|
						 GCNDatagramAlertData.ALERT_TYPE_INTEGRAL|
						 GCNDatagramAlertData.ALERT_TYPE_SWIFT);
			}
			else if(args[i].equals("-group_address"))
			{
				if((i+1) < args.length)
				{
					try
					{
						InetAddress address = null;

						address = InetAddress.getByName(args[i+1]);
						setGroupAddress(address);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing Address:"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(5);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-address requires an address.");
					System.exit(6);
				}
			}
			else if(args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else if(args[i].equals("-hete"))
			{
				addAllowedAlerts(GCNDatagramAlertData.ALERT_TYPE_HETE);
			}
			else if(args[i].equals("-integral"))
			{
				addAllowedAlerts(GCNDatagramAlertData.ALERT_TYPE_INTEGRAL);
			}
			else if(args[i].equals("-max_error_box")||args[i].equals("-meb"))
			{
				if((i+1) < args.length)
				{
					try
					{
						doubleValue = Double.parseDouble(args[i+1]);
						setMaxErrorBox(doubleValue);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing max error box:"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-max_error_box requires a number.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						intValue = Integer.parseInt(args[i+1]);
						setPort(intValue);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing Port:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-port requires a number.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-script"))
			{
				if((i+1) < args.length)
				{
					setScript(args[i+1]);
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-script requires a script.");
					System.exit(6);
				}
			}
			else if(args[i].equals("-swift"))
			{
				addAllowedAlerts(GCNDatagramAlertData.ALERT_TYPE_SWIFT);
			}
			else
			{
				System.err.println("GCNDatagramScriptStarter: Unknown argument "+args[i]+".");
				System.exit(7);
			}
		}// end for
	}

	/**
	 * Help method.
	 */
	protected void help()
	{

		System.out.println("GCNDatagramScriptStarter Help");
		System.out.println("java -Dhttp.proxyHost=wwwcache.livjm.ac.uk "+
				   "-Dhttp.proxyPort=8080 GCNDatagramScriptStarter \n"+
				   "\t[-port <n>][-group_address <address>]"+
				   "\t[-script <filename>][-all][-hete][-integral][-swift]\n"+
				   "\t[-max_error_box|-meb <arcsecs>]");
		System.out.println("-script specifies the script/program to call on a successful alert.");
		System.out.println("-all specifies to call the script for all types of alerts.");
		System.out.println("-hete specifies to call the script for HETE alerts.");
		System.out.println("-integral specifies to call the script for INTEGRAL alerts.");
		System.out.println("-swift specifies to call the script for SWIFT alerts.");
		System.out.println("-max_error_box means only call the script when the error box (radius) is less than that size.");
	}

	// static main
	/**
	 * Main program, for testing GCNDatagramScriptStarter.
	 */
	public static void main(String[] args)
	{
		GCNDatagramScriptStarter gdss = null;
		SimpleDateFormat dateFormat = null;

		// initialise instance
		try
		{
			gdss = new GCNDatagramScriptStarter();
		}
		catch(UnknownHostException e)
		{
			System.err.println("Initialising GCNDatagramScriptStarter failed:"+e);
			e.printStackTrace(System.err);
			System.exit(2);
		}
		// create logger, so gdss will log to file.
		try
		{
			dateFormat = new SimpleDateFormat("yyyy-MM-dd");

			gdss.logger = new GCNDatagramScriptStarterLogger(gdss.getClass().getName()+"-log-"+
								  dateFormat.format(new Date())+".txt");
		}
		catch(Exception e)
		{
			System.err.println("Initialising GCNDatagramScriptStarterLogger failed:"+e);
			e.printStackTrace(System.err);
			System.exit(2);
		}
		gdss.parseArgs(args);
		// run thread
		gdss.run();
		System.exit(0);
	}

	/**
	 * Inner class responsible for keeping track of what a spawned script is doing.
	 */
	public class ScriptThread implements Runnable
	{
		/**
		 * The spawned process we are monitoring.
		 */
		Process process = null;

		public ScriptThread(Process p)
		{
			super();
			process = p;
		}

		/**
		 * Run method for thread.
		 * <ul>
		 * <li>Spawns a InputStreamThread for the process's stdout.
		 * <li>Spawns a InputStreamThread for the process's stderr.
		 * <li>Waits for the process to terminate.
		 * <li>Logs it's exit value.
		 * </ul>
		 * @see #process
		 * @see #logger
		 */
		public void run()
		{
			InputStreamThread ist = null;
			InputStream is = null;
			Thread thread = null;
			int retval = -1;

			// spawn thread to read output from spawned process.
			is = process.getInputStream();
			ist = new InputStreamThread(is,process,"output");
			thread = new Thread(ist);
			thread.start();
			// spawn thread to read stderr from spawned process.
			is = process.getErrorStream();
			ist = new InputStreamThread(is,process,"error");
			thread = new Thread(ist);
			thread.start();
			// this thread waits for spawned script process to terminate.
			try
			{
				retval = process.waitFor();
			}
			catch(InterruptedException ie)
			{
				logger.error(this.getClass().getName()+":run:waitFor failed",ie);
			}
			logger.log(this.getClass().getName()+":run:spawned script returned:"+retval);
		}
	}

	/**
	 * Inner class responsible for reading input from a spawned process and logging it.
	 */
	public class InputStreamThread implements Runnable
	{
		InputStream inputStream = null;
		Process process = null;
		String streamName = null;

		/**
		 * Default constructor.
		 * @param is The input stream.
		 * @param p The process this input stream is attached to.
		 * @param s The name given to this input stream.
		 * @see #inputStream
		 * @see #process
		 * @see #streamName
		 */
		public InputStreamThread(InputStream is,Process p,String s)
		{
			super();
			inputStream = is;
			process = p;
			streamName = s;
		}

		public void run()
		{
			StringBuffer sb = null;
			BufferedInputStream bis = null;
			byte[] buff;
			int retval;

			bis = new BufferedInputStream(inputStream);
			buff = new byte[256];
			sb = new StringBuffer();
			retval = 0;
			while( retval > -1 )
			{
				try
				{
					retval = bis.read(buff,0,buff.length);
				}
				catch(IOException ioe)
				{
					logger.error(streamName+":read failed:",ioe);
				}
				if(retval > -1 )
				{
					for(int i = 0; i < retval; i++)
					{
						sb.append((char)(buff[i]));
						if(((char)(buff[i])) == '\n')
						{
							logger.log(streamName+":"+sb.toString());
							sb.delete(0,sb.length());
						}
					}
				}
			}
		}
	}
}
//
// $Log: not supported by cvs2svn $
// Revision 1.4  2005/01/16 21:16:31  cjm
// Fixed testMpos test.
//
// Revision 1.3  2005/01/16 21:13:48  cjm
// Added initial Integral test notice detection,
// now causes test notices not to start script.
//
// Revision 1.2  2004/12/14 20:56:32  cjm
// Added Swift XRT and UVOT.
// Clarification and fixes to error boxs.
//
// Revision 1.1  2004/10/19 17:10:06  cjm
// Initial revision
//
//
