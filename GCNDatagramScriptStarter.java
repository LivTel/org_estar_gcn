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
 * -ra  &lt;ra&gt; -dec &lt;dec&gt; -epoch &lt;epoch&gt; -error_box &lt;error_box&gt; -trigger_number &lt;tnum&gt; -sequence_number &lt;snum&gt; -grb_date &lt;date&gt; -notice_date &lt;date&gt;
 * </pre>
 * Note the &lt;error_box&gt; is the radius in arc-minutes.
 * <br>
 * The server also supports a command socket, which can be used to configure the GCN Datagram Script Starter.
 * For details of the command socket command set see doControlCommand.
 * @author Chris Mottram
 * @version $Revision: 1.21 $
 * @see #doControlCommand
 */
public class GCNDatagramScriptStarter implements Runnable
{
// constants
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNDatagramScriptStarter.java,v 1.21 2005-03-07 10:49:44 cjm Exp $";
	/**
	 * The default multicast port to listen on, as agreed by Steve.
	 */
	public final static int DEFAULT_MULTICAST_PORT = 2005;
	/**
	 * The default control port to listen on.
	 */
	public final static int DEFAULT_CONTROL_PORT = 2006;
	/**
	 * Default group address for multicast socket. (224.g.r.b).
	 */
	public final static String DEFAULT_GROUP_ADDRESS = "224.103.114.98";
	/**
	 * Length of buffer used for receiving datagram packets.
	 */
	public final static int PACKET_LENGTH = 160;
	/**
	 * Boolean used to determine when to quit the run method.
	 */
	protected boolean quit = false;
	/**
	 * Socket that receives multicast UDP packets from GCN_Server.
	 */
	protected MulticastSocket multicastSocket = null;
	/**
	 * The multicast packet containing the received data.
	 */
	protected DatagramPacket packet = null;
	/**
	 * The input stream used to interate through the bytes of the
	 * datagram packet.
	 */
	protected DataInputStream packetInputStream = null;
	/** 
	 *The InetAddress of the Multicast channel to listen to.
	 */
	protected InetAddress groupAddress = null;
	/** 
	 * The port to attach to.
	 */
	protected int multicastPort = DEFAULT_MULTICAST_PORT;
	/**
	 * Object containing the information parsed from the datagram packet,
	 * used to specify the parameters to the script invocation.
	 */
	protected GCNDatagramAlertData alertData = null;
	/**
	 * Object to syncronize on, when accessing/changing the alertData contents.
	 */
	protected Object alertDataLock = new Object();
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
	 * Bit-mask to run against Swift BAT alert packets.
	 * If a bit in this bit-mask is set in the swift solnStatus we should NOT allow this packet
	 * to trigger a script firing.
	 */
	protected int swiftSolnStatusRejectMask = 0;
	/**
	 * Bit-mask to run against Swift BAT alert packets.
	 * If a bit in this bit-mask is set the correponding bit in the swift solnStatus MUST be set 
	 * to trigger a script firing.
	 */
	protected int swiftSolnStatusAcceptMask = 0;
	/**
	 * The port to run the control port on.
	 * @see #DEFAULT_CONTROL_PORT
	 */
	protected int controlServerPort = DEFAULT_CONTROL_PORT;
	/**
	 * The control server thread instance.
	 */
	protected ControlServerThread controlServerThread = null;
	/**
	 * Boolean specifying whether to start the script when an alert is detected on the Datagram socket.
	 */
	protected boolean enableSocketAlerts = true;
	/**
	 * Boolean specifying whether to start the script when a gamma_ray_burst_alert command is sent 
	 * over the control socket.
	 */
	protected boolean enableManualAlerts = true;

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
	 * <ul>
	 * <li>Initialise quit to false.
	 * <li>Initialise socket (initSocket).
	 * <li>Start a control server thread (startControlServerThread).
	 * <li>While quit is not true:
	 *     <ul>
	 *     <li>Get a datagram packet (receivePacket).
	 *     <li>Acquire the alertData lock (alertDataLock).
	 *     <li>Process the contents of the datagram packet (processData).
	 *     <li>Check whether the packet contents are filtered out or not (alertFilter).
	 *     <li>If the packet contents are not filtered out, start the script (startScript).
	 *     </ul>
	 * </ul>
	 * Any exceptions are caught and an error message printed. But this will cause the script starter to terminate.
	 * @see #quit
	 * @see #initSocket
	 * @see #startControlServerThread
	 * @see #receivePacket
	 * @see #processData
	 * @see #alertFilter
	 * @see #startScript
	 * @see #alertDataLock
	 */
	public void run()
	{
		try
		{
			if(logger != null)
				logger.log(this.getClass().getName()+":run:Started.");
			quit = false;
			initSocket();
			startControlServerThread();
			while(quit == false)
			{
				receivePacket();
				logger.log(this.getClass().getName()+":run:Acquiring alert data lock.");
				synchronized(alertDataLock)
				{
					processData();
					if(alertFilter())
						startScript();
				}
				logger.log(this.getClass().getName()+":run:Released alert data lock.");
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

	/**
	 * Set the port used for the multicast socket.
	 * @param p The port number.
	 * @see #multicastPort
	 */
	public void setMulticastPort(int p)
	{
		multicastPort = p;
	}

	/**
	 * Set the address used for the multicast group address.
	 * @param i The InetAddress.
	 * @see #groupAddress
	 */
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

	/**
	 * Method to set the Swift solnStatus accept bit mask.
	 * Bit-mask to run against Swift BAT alert packets.
	 * If a bit in this bit-mask is set the correponding bit in the swift solnStatus MUST be set 
	 * to trigger a script firing.
	 * @param m An integer representing the mask bits.
	 * @see #swiftSolnStatusAcceptMask
	 */
	public void setSwiftSolnStatusAcceptMask(int m)
	{
		swiftSolnStatusAcceptMask = m;
	}

	/**
	 * Method to set the Swift solnStatus reject bit mask.
	 * Bit-mask to run against Swift BAT alert packets.
	 * If a bit in this bit-mask is set in the swift solnStatus we should NOT allow this packet
	 * to trigger a script firing.
	 * @param m An integer representing the mask bits.
	 * @see #swiftSolnStatusRejectMask
	 */
	public void setSwiftSolnStatusRejectMask(int m)
	{
		swiftSolnStatusRejectMask = m;
	}

	// protected methods.
	/**
	 * Initialise connection.
	 * @see #multicastPort
	 * @see #multicastSocket
	 * @see #groupAddress
	 */
	protected void initSocket() throws Exception
	{
		logger.log(this.getClass().getName()+":initSocket:port = "+multicastPort+" Group Address: "+
				  groupAddress);
		multicastSocket = new MulticastSocket(multicastPort);
		multicastSocket.joinGroup(groupAddress);
	}

	/**
	 * Receive packet.
	 * @see #PACKET_LENGTH
	 * @see #packet
	 * @see #multicastSocket
	 */
	protected void receivePacket() throws Exception
	{
		byte packetBuff[];

		logger.log(this.getClass().getName()+":receivePacket:Started.");
		packetBuff = new byte[PACKET_LENGTH];
		packet = new DatagramPacket(packetBuff,packetBuff.length);
		logger.log(this.getClass().getName()+":receivePacket:Awaiting packet.");
		multicastSocket.receive(packet);
		logger.log(this.getClass().getName()+":receivePacket:Packet received.");
	}

	/**
	 * Process data in packet
	 * @see #packet
	 * @see #packetInputStream
	 * @see #alertData
	 * @see #readImalive
	 * @see #readSax
	 * @see #readHeteAlert
	 * @see #readHeteUpdate
	 * @see #readHeteGroundAnalysis
	 * @see #readIntegralPointing
	 * @see #readIntegralWakeup
	 * @see #readIntegralRefined
	 * @see #readIntegralOffline
	 * @see #readSwiftBatAlert
	 * @see #readSwiftBatGRBPosition
	 * @see #readSwiftXrtGRBPosition
	 * @see #readSwiftUvotGRBPosition
	 */
	protected void processData() throws Exception
	{
		ByteArrayInputStream bin = null;
		byte buff[];

		logger.log(this.getClass().getName()+":processData:Started.");
		buff = packet.getData();
		// Create an input stream from the buffer.
		bin = new ByteArrayInputStream(buff, 0,buff.length);
		packetInputStream = new DataInputStream(bin);
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
		    case 43:
			logger.log(" [HETE_GNDANA]");
			alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_HETE);
			readHeteGroundAnalysis();
			break; 
		    case 51:
			logger.log(" [INTEGRAL_POINTDIR]");
			readIntegralPointing();
			break;
		    case 52:
			logger.log(" [INTEGRAL_SPIACS]");
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
	 * @see #enableSocketAlerts
	 */
	protected boolean alertFilter()
	{
		if((allowedAlerts & alertData.getAlertType()) == 0)
		{
			logger.log("alertFilter stopped propogation of alert on type: allowed alerts "+allowedAlerts+
				   " not compatible with alertData alert type "+alertData.getAlertType()+".");
			return false;
		}
		if(enableSocketAlerts == false)
		{
			logger.log("alertFilter stopped propogation of alert. "+
				   "Socket alerts have been disabled from the control socket.");
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
		// ensure RA filled in
		if(alertData.getRA() == null)
		{
			logger.log("alertFilter stopped propogation of alert: RA was NULL.");
			return false;
		}
		// ensure Dec filled in
		if(alertData.getDec() == null)
		{
			logger.log("alertFilter stopped propogation of alert: Dec was NULL.");
			return false;
		}
		// special Swift solnStatus (word 18) filtering
		if((alertData.getAlertType()) == GCNDatagramAlertData.ALERT_TYPE_SWIFT)
		{
			// Ensure no bits in swiftSolnStatusAcceptMask are also in
			// swiftSolnStatusRejectMask, which would be stupid (no Swift alerts would be propogated).
			if((swiftSolnStatusRejectMask & swiftSolnStatusAcceptMask) != 0)
			{
				logger.log("alertFilter detected stupid solnStatus masks : Accept:0x"+
					   Integer.toHexString(swiftSolnStatusAcceptMask)+"  Reject:0x"+
					   Integer.toHexString(swiftSolnStatusRejectMask)+".");
			}
			else
			{
				// If a bit in the reject bit-mask is set in the swift solnStatus 
				// we should NOT allow this packet to trigger a script firing.
				if((alertData.getStatus() & swiftSolnStatusRejectMask) != 0)
				{
					logger.log("alertFilter stopped propogation of the alert: solnStatus 0x"+
						   Integer.toHexString(alertData.getStatus())+
						   " contains bits in reject mask 0x"+
						   Integer.toHexString(swiftSolnStatusRejectMask)+".");
					return false;
				}
				// If a bit in the accept bit-mask is set the correponding bit in the swift solnStatus 
				// MUST be set to trigger a script firing.
				if((alertData.getStatus() & swiftSolnStatusAcceptMask) != swiftSolnStatusAcceptMask)
				{
					logger.log("alertFilter stopped propogation of the alert: solnStatus 0x"+
						   Integer.toHexString(alertData.getStatus())+
						   " does NOT contain bits in accept mask 0x"+
						   Integer.toHexString(swiftSolnStatusAcceptMask)+".");
					return false;
				}
			}// end if swift solnStatus bitmasks are not stupid
		}// end if swift
		return true;
	}

	/**
	 * Method to call the script. The script is started with parameters as follows:
	 * <pre>
	 * -ra  &lt;ra&gt; -dec &lt;dec&gt; -epoch &lt;epoch&gt; -error_box &lt;error_box&gt; -trigger_number &lt;tnum&gt; -sequence_number &lt;snum&gt; -grb_date &lt;date&gt; -notice_date &lt;date&gt;
	 * </pre>
	 * A <b>-test</b> argument is added if specified in the alertData.
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
		if(alertData.getTest())
			execString.append(" -test");
		logger.log("startScript: Executing:"+execString.toString());
		process = rt.exec(execString.toString());
		scriptThread = new ScriptThread(process);
		thread = new Thread(scriptThread);
		thread.start();
	}

	/**
	 * Read a type integer from the packet input stream, and return it.
	 * @see #packetInputStream
	 */
	protected int readType() throws IOException
	{
		int type = packetInputStream.readInt();	
		return type;
	}
    
	/**
	 * Read a termintor word from the packet input stream.
	 * @see #packetInputStream
	 * @see #logger
	 */
	protected void readTerm() throws IOException
	{
		packetInputStream.readByte();
		packetInputStream.readByte();
		packetInputStream.readByte();
		packetInputStream.readByte();
		logger.log("-----Terminator");
	}
    
	/** 
	 * Read the header and hop count word, and log it. 
	 * @see #packetInputStream
	 * @see #logger
	 */
	protected void readHdr() throws IOException
	{
		int seq  = packetInputStream.readInt(); // SEQ_NO.
		int hop  = packetInputStream.readInt(); // HOP_CNT. 
		logger.log("Header: Packet Seq.No: "+seq+" Hop Count: "+hop);
	}
    
	/** 
	 * Read the SOD for date.
	 * @see #packetInputStream
	 * @see #logger
	 */
	protected void readSod() throws IOException
	{
		int sod = packetInputStream.readInt();
		logger.log("SOD: "+sod);
	}
    
	/** 
	 * Read stuffing bytes. 
	 * @see #packetInputStream
	 * @see #logger
	 */
	protected void readStuff(int from, int to) throws IOException
	{
		for (int i = from; i <= to; i++)
		{
			packetInputStream.readInt();
		}
		logger.log("Skipped: "+from+" to "+to);
	}

	/**
	 * IAMALIVE packets.
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
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

	/**
	 * Read sax packets.
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 */
	public void readSax()
	{
		try
		{
			readHdr(); // 0, 1, 2
			readSod();     // 3
			readStuff(4,4);   // 4 - spare
			int burst_tjd = packetInputStream.readInt(); // 5 - burst_tjd
			int burst_sod = packetInputStream.readInt(); // 6 - burst_sod
			logger.log("Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
			int bra =  packetInputStream.readInt(); // 7 - burst RA [ x10000 degrees]
			int bdec = packetInputStream.readInt(); // 8 - burst Dec [x10000 degrees].
			int bint = packetInputStream.readInt(); // 9 - burst intens mCrab.
			logger.log("RA: "+bra+" Dec: "+bdec+" Intensity:"+bint+" [mcrab]");
			readStuff(10, 10);   // 10 - spare
			int berr  = packetInputStream.readInt(); // 11 - burst error
			int bconf = packetInputStream.readInt(); // 12 - burst conf [% x 100].
			logger.log("Burst Error: "+berr+" Confidence: "+bconf);
			readStuff(13, 17); // 13,, 17 - spare.		
			int trig_id = packetInputStream.readInt(); // 18 - trigger flags.
			logger.log("Trigger Flags: "+trig_id);
			packetInputStream.readInt(); // 19 - stuff.
			readStuff(20, 38); // 20,, 38 - spare.
			readTerm(); // 39 - TERM.	   
		}
		catch (IOException e)
		{
			logger.error("SAX_WFC_POS: Error reading: ",e);
			alertData.setAlertType(0);
		}
	}

	/**
	 * Read HETE alert packets.
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 */
	public void readHeteAlert()
	{
		try
		{
			readHdr(); // 0, 1, 2
			readSod();     // 3
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int burst_tjd = packetInputStream.readInt(); // 5 - burst_tjd
			int burst_sod = packetInputStream.readInt(); // 6 - burst_sod
			logger.log("Trig. Seq. No: "+tsn+" Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
			readStuff(7, 8); // 7, 8 - spare 
			//int trig_flags = GAMMA_TRIG | WXM_TRIG | PROB_GRB;
			int trig_flags = packetInputStream.readInt(); // 9 - trig_flags
			logger.log("Trigger Flags: "+trig_flags);
			int gamma = packetInputStream.readInt();   // 10 - gamma_cnts
			int wxm = packetInputStream.readInt(); // 11 - wxm_cnts
			int sxc = packetInputStream.readInt();  // 12 - sxc_cnts
			logger.log("Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
			int gammatime = packetInputStream.readInt(); // 13 - gamma_time
			int wxmtime = packetInputStream.readInt(); // 14 - wxm_time
			int scpoint = packetInputStream.readInt(); // 15 - sc_point
			logger.log("Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
			logger.log("SC Point:"+scpoint);
			readStuff(16, 38); // 16,, 38 spare
			readTerm(); // 39 - TERM.

		}
		catch  (IOException e)
		{
			logger.error("HETE ALERT:readHeteAlert: ",e);
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * HETE_S/C_UPDATE (TYPE=41).
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
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
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			trigNum = (tsn & 0x0000FFFF);
			mesgNum = (tsn & 0xFFFF0000) >> 16;
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			int burstTjd = packetInputStream.readInt(); // 5 - burst_tjd
			int burstSod = packetInputStream.readInt(); // 6 - burst_sod
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			logger.log("Burst: TJD:"+burstTjd+" SOD: "+burstSod);
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			bra = packetInputStream.readInt(); // Burst RA (x10e4 degs). // 7 - burst_ra
			bdec = packetInputStream.readInt(); // Burst Dec (x10e4 degs). // 8 = burst_dec
			// if neither WXM or SXC have positions, than bra/bdec is -999.9999 (x10000)
			if((bra < -999000)||(bdec < -999000))
			{
				logger.log("RA/Dec out of range: bra (x10000) = "+bra+" bdec (x10000) = "+bdec);
				alertData.setAlertType(0); // ensure this is not propogated as an alert
			}
			else
			{
				ra = new RA();
				dec = new Dec();
				ra.fromRadians(Math.toRadians((double)bra)/10000.0);
				dec.fromRadians(Math.toRadians((double)bdec)/10000.0);
				logger.log("Burst RA: "+ra);
				logger.log("Burst Dec: "+dec);
				logger.log("Epoch: "+burstDate);
			}
			int trig_flags = packetInputStream.readInt(); // 9 - trig_flags
			logger.log("Trigger Flags: 0x"+Integer.toHexString(trig_flags));
			int gamma = packetInputStream.readInt();   // 10 - gamma_cnts
			int wxm   = packetInputStream.readInt(); // 11 - wxm_cnts
			int sxc   = packetInputStream.readInt();  // 12 - sxc_cnts
			logger.log("Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
			int gammatime = packetInputStream.readInt(); // 13 - gamma_time
			int wxmtime = packetInputStream.readInt(); // 14 - wxm_time
			int scpoint = packetInputStream.readInt(); // 15 - sc_point
			int sczra   = (scpoint & 0xFFFF0000) >> 16;
			int sczdec  = (scpoint & 0x0000FFFF);
			logger.log("Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
			logger.log("SC Pointing: RA(deg): "+(((double)sczra)/10000.0)+
					   " Dec(deg): "+(((double)sczdec)/10000.0));
			int wxra1 = packetInputStream.readInt();  // 16 - WXM ra1 (x10e4 degs).
			int wxdec1 = packetInputStream.readInt(); // 17 WXM dec1 (x10e4 degs).
			int wxra2 = packetInputStream.readInt();  // 18 - WXM ra2 (x10e4 degs).
			int wxdec2 = packetInputStream.readInt(); // 19 WXM dec2 (x10e4 degs).
			int wxra3 = packetInputStream.readInt();  // 20 - WXM ra3 (x10e4 degs).
			int wxdec3 = packetInputStream.readInt(); // 21 WXM dec3 (x10e4 degs).
			int wxra4 = packetInputStream.readInt();  // 22 - WXM ra4 (x10e4 degs).
			int wxdec4 = packetInputStream.readInt(); // 23 WXM dec4 (x10e4 degs).
			int wxErrors = packetInputStream.readInt(); // 24 WXM Errors (bit-field) - Sys & Stat.
			// wxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits.
			logger.log("WXM error box (radius,arcsec) : statistical : "+((wxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(wxErrors&0x0000FFFF)+".");
			int wxDimSig = packetInputStream.readInt(); // 25 WXM Packed numbers.
			// wxDimSig contains the maximum dimension of the WXM error box [units arcsec] in top 16 bits
			int wxErrorBoxArcsec = (wxDimSig&0xFFFF0000)>>16;
			logger.log("WXM error box (diameter,arcsec) : "+wxErrorBoxArcsec+".");
			int sxra1 = packetInputStream.readInt();  // 26 - SC ra1 (x10e4 degs).
			int sxdec1 = packetInputStream.readInt(); // 27 SC dec1 (x10e4 degs).
			int sxra2 = packetInputStream.readInt();  // 28 - SC ra2 (x10e4 degs).
			int sxdec2 = packetInputStream.readInt(); // 29 SC dec2 (x10e4 degs).
			int sxra3 = packetInputStream.readInt();  // 30 - SC ra3 (x10e4 degs).
			int sxdec3 = packetInputStream.readInt(); // 31 SC dec3 (x10e4 degs).
			int sxra4 = packetInputStream.readInt();  // 32 - SC ra4 (x10e4 degs).
			int sxdec4 = packetInputStream.readInt(); // 33 SC dec4 (x10e4 degs).
			int sxErrors = packetInputStream.readInt(); // 34 SC Errors (bit-field) - Sys & Stat.
			// sxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits).
			logger.log("SXC error box (radius,arcsec) : statistical : "+((sxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(sxErrors&0x0000FFFF)+".");
			int sxDimSig = packetInputStream.readInt(); // 35 SC Packed numbers.
			// sxDimSig contains the maximum dimension of the SXC error box [units arcsec] in top 16 bits
			int sxErrorBoxArcsec = (sxDimSig&0xFFFF0000)>>16;
			logger.log("SXC error box (diameter,arcsec) : "+sxErrorBoxArcsec+".");
			alertData.setErrorBoxSize(((double)(Math.max(wxErrorBoxArcsec,sxErrorBoxArcsec)))/
						  (2.0*60.0));// radius, in arc-min
			int posFlags = packetInputStream.readInt(); // 36 - pos_flags
			logger.log("Pos Flags: 0x"+Integer.toHexString(posFlags));
			int validity = packetInputStream.readInt(); // 37 - validity flags.
			logger.log("Validity Flag: 0x"+Integer.toHexString(validity));
			// There are two flags BURST_VALID (0x1) and BURST_INVALID (0x2)
			// Neither, one or both(?) can be set.
			// Currently, follow anything that is not explicitly INVALID
			// If bit 2 is NOT set, the burst is NOT INVALID.
			if((validity & 0x00000002) != 0x00000002)
			{
				alertData.setRA(ra);
				alertData.setDec(dec);
				// epoch is "current", is this burst date or notice date?
				alertData.setEpoch(burstDate);
			}
			else
			{
				logger.log("BURST INVALID:RA/Dec not set.");
				alertData.setAlertType(0); // ensure this is not propogated as an alert
			}
			readStuff(38, 38); // 38 -spare
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("HETE UPDATE: Error reading: ",e);
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * HETE_GNDANA (TYPE=43).
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
	 */
	public void readHeteGroundAnalysis()
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
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			trigNum = (tsn & 0x0000FFFF);
			mesgNum = (tsn & 0xFFFF0000) >> 16;
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			int burstTjd = packetInputStream.readInt(); // 5 - burst_tjd
			int burstSod = packetInputStream.readInt(); // 6 - burst_sod
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			logger.log("Burst: TJD:"+burstTjd+" SOD: "+burstSod);
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			bra = packetInputStream.readInt(); // Burst RA (x10e4 degs). // 7 - burst_ra
			bdec = packetInputStream.readInt(); // Burst Dec (x10e4 degs). // 8 = burst_dec
			// if neither WXM or SXC have positions, than bra/bdec is -999.9999 (x10000)
			if((bra < -999000)||(bdec < -999000))
			{
				logger.log("RA/Dec out of range: bra (x10000) = "+bra+" bdec (x10000) = "+bdec);
				alertData.setAlertType(0); // ensure this is not propogated as an alert
			}
			else
			{
				ra = new RA();
				dec = new Dec();
				ra.fromRadians(Math.toRadians((double)bra)/10000.0);
				dec.fromRadians(Math.toRadians((double)bdec)/10000.0);
				logger.log("Burst RA: "+ra);
				logger.log("Burst Dec: "+dec);
				logger.log("Epoch: "+burstDate);
			}
			int trig_flags = packetInputStream.readInt(); // 9 - trig_flags
			logger.log("Trigger Flags: 0x"+Integer.toHexString(trig_flags));
			int gamma = packetInputStream.readInt();   // 10 - gamma_cnts
			int wxm   = packetInputStream.readInt(); // 11 - wxm_cnts
			int sxc   = packetInputStream.readInt();  // 12 - sxc_cnts
			logger.log("Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
			int gammatime = packetInputStream.readInt(); // 13 - gamma_time
			int wxmtime = packetInputStream.readInt(); // 14 - wxm_time
			int scpoint = packetInputStream.readInt(); // 15 - sc_point
			int sczra   = (scpoint & 0xFFFF0000) >> 16;
			int sczdec  = (scpoint & 0x0000FFFF);
			logger.log("Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
			logger.log("SC Pointing: RA(deg): "+(((double)sczra)/10000.0)+
					   " Dec(deg): "+(((double)sczdec)/10000.0));
			int wxra1 = packetInputStream.readInt();  // 16 - WXM ra1 (x10e4 degs).
			int wxdec1 = packetInputStream.readInt(); // 17 WXM dec1 (x10e4 degs).
			int wxra2 = packetInputStream.readInt();  // 18 - WXM ra2 (x10e4 degs).
			int wxdec2 = packetInputStream.readInt(); // 19 WXM dec2 (x10e4 degs).
			int wxra3 = packetInputStream.readInt();  // 20 - WXM ra3 (x10e4 degs).
			int wxdec3 = packetInputStream.readInt(); // 21 WXM dec3 (x10e4 degs).
			int wxra4 = packetInputStream.readInt();  // 22 - WXM ra4 (x10e4 degs).
			int wxdec4 = packetInputStream.readInt(); // 23 WXM dec4 (x10e4 degs).
			int wxErrors = packetInputStream.readInt(); // 24 WXM Errors (bit-field) - Sys & Stat.
			// wxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits.
			logger.log("WXM error box (radius,arcsec) : statistical : "+((wxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(wxErrors&0x0000FFFF)+".");
			int wxDimSig = packetInputStream.readInt(); // 25 WXM Packed numbers.
			// wxDimSig contains the maximum dimension of the WXM error box [units arcsec] in top 16 bits
			int wxErrorBoxArcsec = (wxDimSig&0xFFFF0000)>>16;
			logger.log("WXM error box (diameter,arcsec) : "+wxErrorBoxArcsec+".");
			int sxra1 = packetInputStream.readInt();  // 26 - SC ra1 (x10e4 degs).
			int sxdec1 = packetInputStream.readInt(); // 27 SC dec1 (x10e4 degs).
			int sxra2 = packetInputStream.readInt();  // 28 - SC ra2 (x10e4 degs).
			int sxdec2 = packetInputStream.readInt(); // 29 SC dec2 (x10e4 degs).
			int sxra3 = packetInputStream.readInt();  // 30 - SC ra3 (x10e4 degs).
			int sxdec3 = packetInputStream.readInt(); // 31 SC dec3 (x10e4 degs).
			int sxra4 = packetInputStream.readInt();  // 32 - SC ra4 (x10e4 degs).
			int sxdec4 = packetInputStream.readInt(); // 33 SC dec4 (x10e4 degs).
			int sxErrors = packetInputStream.readInt(); // 34 SC Errors (bit-field) - Sys & Stat.
			// sxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits).
			logger.log("SXC error box (radius,arcsec) : statistical : "+((sxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(sxErrors&0x0000FFFF)+".");
			int sxDimSig = packetInputStream.readInt(); // 35 SC Packed numbers.
			// sxDimSig contains the maximum dimension of the SXC error box [units arcsec] in top 16 bits
			int sxErrorBoxArcsec = (sxDimSig&0xFFFF0000)>>16;
			logger.log("SXC error box (diameter,arcsec) : "+sxErrorBoxArcsec+".");
			alertData.setErrorBoxSize(((double)(Math.max(wxErrorBoxArcsec,sxErrorBoxArcsec)))/
						  (2.0*60.0));// radius, in arc-min
			int posFlags = packetInputStream.readInt(); // 36 - pos_flags
			logger.log("Pos Flags: 0x"+Integer.toHexString(posFlags));
			int validity = packetInputStream.readInt(); // 37 - validity flags.
			logger.log("Validity Flag: 0x"+Integer.toHexString(validity));
			// There are two flags BURST_VALID (0x1) and BURST_INVALID (0x2)
			// Neither, one or both(?) can be set.
			// Currently, follow anything that is not explicitly INVALID
			// If bit 2 is NOT set, the burst is NOT INVALID.
			if((validity & 0x00000002) != 0x00000002)
			{
				alertData.setRA(ra);
				alertData.setDec(dec);
				// epoch is "current", is this burst date or notice date?
				alertData.setEpoch(burstDate);
			}
			else
			{
				logger.log("BURST INVALID:RA/Dec not set.");
				alertData.setAlertType(0); // ensure this is not propogated as an alert
			}
			readStuff(38, 38); // 38 -spare
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("HETE Ground Analysis: Error reading: ",e);
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Read integral pointing.
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 */
	public void readIntegralPointing()
	{
		RA ra = null;
		Dec dec = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			int slewTjd = packetInputStream.readInt(); // 5 Slew TJD.
			int slewSod = packetInputStream.readInt(); // 6 Slew SOD.
			logger.log("Slew at: "+slewTjd+" TJD Time: "+slewSod+" Sod.");
			readStuff(7, 11);
			int flags   =  packetInputStream.readInt(); // 12 Test Flags.
			logger.log("Test Flags: ["+Integer.toHexString(flags).toUpperCase()+"]");
			packetInputStream.readInt(); // 13 spare.
			int scRA    = packetInputStream.readInt(); // 14 Next RA *10000.
			int scDec   = packetInputStream.readInt(); // 15 Next Dec *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians((double)scRA)/10000.0);
			dec.fromRadians(Math.toRadians((double)scDec)/10000.0);
			logger.log("SC Slew to RA: "+ra+" Dec:"+dec);
			readStuff(16,18);
			int scStat  = packetInputStream.readInt(); // 19 Status and attitude flags.
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
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
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
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = packetInputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = packetInputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
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
			int detFlags   =  packetInputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  packetInputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  packetInputStream.readInt(); // 11 burst error (arcsec)
			// burstError is radius of circle (arcsecs) that contains TBD% c.l.  of bursts
			alertData.setErrorBoxSize((((double)burstError)/60.0));// in arc-min
			int testMpos = packetInputStream.readInt(); // 12 Test/Multi-Position flags.
			logger.log("Status Flags: [0x"+Integer.toHexString(testMpos).toUpperCase()+"]");
			logger.log("testMpos 0x"+Integer.toHexString(testMpos).toUpperCase()+
				   " & 0x"+Integer.toHexString((1<<31)).toUpperCase()+" = "+(testMpos & (1<<31)));
			if((testMpos & (1<<31))!=0)
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
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Integral Refined (TYPE 54).
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
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
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = packetInputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = packetInputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
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
			int detFlags   =  packetInputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  packetInputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  packetInputStream.readInt(); // 11 burst error (arcsec)
			// burstError is radius of circle (arcsecs) that contains TBD% c.l.  of bursts
			logger.log("Burst error: "+((double)burstError)+" arcsec radius.");
			alertData.setErrorBoxSize((((double)burstError)/60.0));// in arc-min
			int testMpos = packetInputStream.readInt(); // 12 Test/Multi-Position flags.
			logger.log("Status Flags: [0x"+Integer.toHexString(testMpos).toUpperCase()+"]");
			logger.log("testMpos 0x"+Integer.toHexString(testMpos).toUpperCase()+
				   " & 0x"+Integer.toHexString((1<<31)).toUpperCase()+" = "+(testMpos & (1<<31)));
			if((testMpos & (1<<31))!=0)
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
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Integral offline.
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
	 */
	public void readIntegralOffline()
	{
		RA ra = null;
		Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = packetInputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = packetInputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
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
			int detFlags   =  packetInputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  packetInputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			// burstError is radius of circle (arcsecs) that contains TBD% c.l.  of bursts
			int burstError   =  packetInputStream.readInt(); // 11 burst error (arcsec)
			logger.log("Burst error: "+((double)burstError)+" arcsec radius.");
			alertData.setErrorBoxSize((((double)burstError)/60.0));// in arc-min
			int testMpos = packetInputStream.readInt(); // 12 Test/Multi-Position flags.
			logger.log("Status Flags: [0x"+Integer.toHexString(testMpos).toUpperCase()+"]");
			logger.log("testMpos 0x"+Integer.toHexString(testMpos).toUpperCase()+
				   " & 0x"+Integer.toHexString((1<<31)).toUpperCase()+" = "+(testMpos & (1<<31)));
			if((testMpos & (1<<31))!=0)
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
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Swift BAT alert (Type 60).
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
	 */
	public void readSwiftBatAlert()
	{
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
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
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Swift BAT position (Type 61,SWIFT_BAT_GRB_POSITION).
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
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
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x00FFFFFF);
			int mesgNum = (tsn & 0xFF000000) >> 24;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = packetInputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = packetInputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
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
			int burstFlue = packetInputStream.readInt(); // 9 Burst flue (counts) number of events.
			int burstIPeak = packetInputStream.readInt(); // 10 Burst ipeak (counts*ff) counts.
			int burstError = packetInputStream.readInt(); // 11 Burst error degrees (0..180) * 10000)
			// burst error is radius of circle in degrees*10000 containing TBD% of bursts!
			// Initially, hardwired to 4 arcmin (0.067 deg) radius.
			alertData.setErrorBoxSize((((double)burstError)*60.0)/10000.0);// in arc-min
			logger.log("Error Box Radius (arcmin): "+((((double)burstError)*60.0)/10000.0));
			readStuff(12, 17);// Phi, theta, integ_time, spare x 2
			int solnStatus = packetInputStream.readInt(); // 18 Type of source found (bitfield)
			logger.log("Soln Status : 0x"+Integer.toHexString(solnStatus));
			alertData.setStatus(solnStatus); // set alert data status bits to solnStatus
			if((solnStatus & (1<<0))>0)
				logger.log("Soln Status : A point source was found.");
			if((solnStatus & (1<<1))>0)
				logger.log("Soln Status : It is a GRB.");
			if((solnStatus & (1<<2))>0)
				logger.log("Soln Status : It is an interesting source.");
			if((solnStatus & (1<<3))>0)
				logger.log("Soln Status : It is a flight catalogue source.");
			if((solnStatus & (1<<4))>0)
				logger.log("Soln Status : It is an image trigger.");
			else
				logger.log("Soln Status : It is a rate trigger.");
			if((solnStatus & (1<<5))>0)
				logger.log("Soln Status : It is defintely not a GRB (ground-processing assigned).");
			if((solnStatus & (1<<6))>0)
				logger.log("Soln Status : It is probably not a GRB (high background level).");
			if((solnStatus & (1<<7))>0)
				logger.log("Soln Status : It is probably not a GRB (low image significance).");
			if((solnStatus & (1<<8))>0)
				logger.log("Soln Status : It is a ground catalogue source.");
			if((solnStatus & (1<<9))>0)
				logger.log("Soln Status : It is an X-ray burster (automated ground assignment).");
			if((solnStatus & (1<<10))>0)
				logger.log("Soln Status : It is an AGN source.");
			int misc = packetInputStream.readInt(); // 19 Misc (bitfield)
			logger.log("Misc Bits : 0x"+Integer.toHexString(misc));
			int imageSignif = packetInputStream.readInt(); // 20 Image Significance (sig2noise *100)
			logger.log("Image Significance (SN sigma) : "+(((double)imageSignif)/100.0));
			int rateSignif = packetInputStream.readInt(); // 21 Rate Significance (sig2noise *100)
			logger.log("Rate Significance (SN sigma) : "+(((double)rateSignif)/100.0));
			readStuff(22, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT BAT GRB POSITION: Error reading: ",e);
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Swift XRT position (Type 67,SWIFT_GRB_XRT_POSITION).
	 * @see #swiftSolnStatusAcceptMask
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
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
			int tsn = packetInputStream.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x00FFFFFF);
			int mesgNum = (tsn & 0xFF000000) >> 24;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = packetInputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = packetInputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
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
			int burstFlux = packetInputStream.readInt(); // 9 Burst flux (counts) number of events.
			readStuff(10, 10); // 10 spare.
			int burstError = packetInputStream.readInt(); // 11 Burst error degrees (0..180) * 10000.
			// burst error is radius of circle in degrees*10000 containing 90% of bursts.
			// Initially, hardwired to 9".
			alertData.setErrorBoxSize((((double)burstError)*60.0)/10000.0);// in arc-min
			logger.log("Error Box Radius (arcmin): "+((((double)burstError)*60.0)/10000.0));
			// alertFilter checks SWIFT alerts to ensure the status (Swift BAT solnStatus (word 18))
			// has the correct bits set. XRT alerts don't have solnStatus bits, but we must set
			// the status bits to fool alertFilter. We set to swiftSolnStatusAcceptMask to should
			// always pass the test, assuming a bit in swiftSolnStatusAcceptMask is NOT also in
			// swiftSolnStatusRejectMask, which would be stupid (no Swift alerts would be propogated).
			alertData.setStatus(swiftSolnStatusAcceptMask); // set alert data status bits to solnStatus
			readStuff(12, 38);// X_TAM, Amp_Wave, misc, det_sig plus lots of spares.
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT XRT GRB POS: Error reading: ",e);
			alertData.setAlertType(0); // ensure this is not propogated as an alert
		}
	}

	/**
	 * Swift XRT position (Type 81,SWIFT_UVOT_POSITION).
	 * @see #swiftSolnStatusAcceptMask
	 * @see #readHdr
	 * @see #readSod
	 * @see #readStuff
	 * @see #readTerm
	 * @see #packetInputStream
	 * @see #logger
	 * @see #alertData
	 * @see #truncatedJulianDateSecondOfDayToDate
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
			int tsn = packetInputStream.readInt();   // 4 - trig_obs_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			alertData.setTriggerNumber(trigNum);
			alertData.setSequenceNumber(mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = packetInputStream.readInt(); // 5 Burst TJD.
			int burstSod = packetInputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			alertData.setGRBDate(burstDate);
			int bra    = packetInputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = packetInputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
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
			int burstMag = packetInputStream.readInt(); // 9 Uvot mag * 100
			readStuff(10, 10); // 10 filter integer.
			int burstError = packetInputStream.readInt(); // 11 Burst error in centi-degrees (0..180.0)*10000.
			// burst error is radius of circle in degrees*10000 containing 90% of bursts.
			// Initially, hardwired to 9".
			alertData.setErrorBoxSize((((double)burstError)*60.0)/10000.0);// in arc-min
			logger.log("Error Box Radius (arcmin): "+((((double)burstError)*60.0)/10000.0));
			readStuff(12, 38);// misc plus lots of spares.
			// alertFilter checks SWIFT alerts to ensure the status (Swift BAT solnStatus (word 18))
			// has the correct bits set. UVOT alerts don't have solnStatus bits, but we must set
			// the status bits to fool alertFilter. We set to swiftSolnStatusAcceptMask to should
			// always pass the test, assuming a bit in swiftSolnStatusAcceptMask is NOT also in
			// swiftSolnStatusRejectMask, which would be stupid (no Swift alerts would be propogated).
			alertData.setStatus(swiftSolnStatusAcceptMask); // set alert data status bits to solnStatus
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.error("SWIFT UVOT GRB POS: Error reading: ",e);
			alertData.setAlertType(0); // ensure this is not propogated as an alert
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
	 * Method to start a control server thread.
	 * @see GCNDatagramScriptStarter.ControlServerThread
	 * @see #controlServerThread
	 * @see #controlServerPort
	 */
	protected void startControlServerThread()
	{
		Thread t = null;

		controlServerThread = new ControlServerThread();
		controlServerThread.setPort(controlServerPort);
		t = new Thread(controlServerThread);
		t.start();
	}

	/**
	 * Method to perform a command send over the control socket.
	 * The following commands are supported:
	 * <pre>
	 * disable [all|socket|manual|status]
	 * enable [all|socket|manual|status]
	 * gamma_ray_burst_alert -ra &lt;ra&gt; -dec &lt;dec&gt; -epoch &lt;epoch&gt; -error_box &lt;error_box&gt; -trigger_number &lt;n&gt; -sequence_number &lt;n&gt; -grb_date &lt;date&gt; -notice_date &lt;date&gt; -HETE -SWIFT -INTEGRAL -test
	 * help
	 * quit
	 * test
	 * </pre>
	 * Dates specified in the form: yyyy-MM-dd'T'HH:mm:ss.
	 * -ra specified as HH:MM:SS.ss.
	 * -dec specified as [+|-]DD:MM:SS.ss.
	 * -error_box specified as a radius in decimal arc-minutes.
	 * @param args An array of string containing the command name, and it's arguments.
	 * @return A string, containing the return string to send back over the control socket to the connected
	 *         client.
	 * @see #doGammaRayBurstAlertControlCommand
	 * @see #quit
	 */
	protected String doControlCommand(String args[])
	{
		try
		{
			if(args.length < 1)
			{
				return new String("No command specified.\n");
			}
			if(args[0].equals("disable"))
			{
				if(args.length == 1)
				{
					enableSocketAlerts = false;
					enableManualAlerts = false;
					logger.log("doControlCommand:All alerts disabled.");
					return new String("All alerts disabled.\n");
				}
				else if(args.length == 2)
				{
					if(args[1].equals("all"))
					{
						enableSocketAlerts = false;
						enableManualAlerts = false;
						logger.log("doControlCommand:All alerts disabled.");
						return new String("All alerts disabled.\n");
					}
					else if(args[1].equals("socket"))
					{
						enableSocketAlerts = false;
						logger.log("doControlCommand:Socket alerts disabled.");
						return new String("Socket alerts disabled.\n");
					}
					else if(args[1].equals("manual"))
					{
						enableManualAlerts = false;
						logger.log("doControlCommand:Manual alerts disabled.");
						return new String("Manual alerts disabled.\n");
					}
					else if(args[1].equals("status"))
					{
						logger.log("doControlCommand:Socket alerts enable:"+enableSocketAlerts+
								  ", Manual alerts enable:"+enableManualAlerts+".");
						return new String("Socket alerts enable:"+enableSocketAlerts+
								  ", Manual alerts enable:"+enableManualAlerts+".\n");
					}
					else
						return new String("Illegal disable command : disable [all|socket|manual|status].\n");		
				}
				else
					return new String("Illegal disable command : disable [all|socket|manual|status].\n");
			}
			else if(args[0].equals("enable"))
			{
				if(args.length == 1)
				{
					enableSocketAlerts = true;
					enableManualAlerts = true;
					logger.log("doControlCommand:All alerts enabled.");
					return new String("All alerts enabled.\n");
				}
				else if(args.length == 2)
				{
					if(args[1].equals("all"))
					{
						enableSocketAlerts = true;
						enableManualAlerts = true;
						logger.log("doControlCommand:All alerts enabled.");
						return new String("All alerts enabled.\n");
					}
					else if(args[1].equals("socket"))
					{
						enableSocketAlerts = true;
						logger.log("doControlCommand:Socket alerts enabled.");
						return new String("Socket alerts enabled.\n");
					}
					else if(args[1].equals("manual"))
					{
						enableManualAlerts = true;
						logger.log("doControlCommand:Manual alerts enabled.");
						return new String("Manual alerts enabled.\n");
					}
					else if(args[1].equals("status"))
					{
						logger.log("doControlCommand:Socket alerts enable:"+enableSocketAlerts+
								  ", Manual alerts enable:"+enableManualAlerts+"");
						return new String("Socket alerts enable:"+enableSocketAlerts+
								  ", Manual alerts enable:"+enableManualAlerts+".\n");
					}
					else
						return new String("Illegal enable command : enable [all|socket|manual|status].\n");		
				}
				else
					return new String("Illegal enable command : enable [all|socket|manual|status].\n");
			}
			else if(args[0].equals("gamma_ray_burst_alert"))
			{
				String returnString = null;
				returnString = doGammaRayBurstAlertControlCommand(args);
				return returnString;
			}
			else if(args[0].equals("help"))
			{
				return new String("GCNDatagramAlertData Command Server Help:\n"+
						  "\tdisable [all|socket|manual|status]\n"+
						  "\tenable [all|socket|manual|status]\n"+
						  "\tgamma_ray_burst_alert -ra <ra> -dec <dec> -epoch <epoch> -error_box <error_box> -trigger_number <n> -sequence_number <n> -grb_date <date> -notice_date <date> -HETE -SWIFT -INTEGRAL -test\n"+
						  "\thelp\n"+
						  "\tquit\n"+
						  "\ttest\n"+
						  "Dates specified in the form: yyyy-MM-dd'T'HH:mm:ss\n"+
						  "-ra specified as HH:MM:SS.ss\n"+
						  "-dec specified as [+|-]DD:MM:SS.ss\n"+
						  "-error_box specified as a radius in decimal arc-minutes\n");
			}
			else if(args[0].equals("quit"))
			{
				quit();
				if(controlServerThread != null)
					controlServerThread.quit();
				logger.log("doControlCommand:Quiting GCNDatagramScriptStarter.");
				return new String("Quiting GCNDatagramScriptStarter.\n");
			}
			else if(args[0].equals("test"))
			{
				logger.log("doControlCommand:Test command received.");
				return new String("Test command received.\n");
			}
			return new String("Unknown command:"+args[0]+"\n");
		}
		catch(Exception e)
		{
			return new String("doControlCommand:An Exception occured:"+e+"\n");
		}
	}

	/**
	 * Method to perform a manual script start using the gamma_ray_burst_alert command from the control socket.
	 * @param args An array of string containing the command name, and it's arguments.
	 * @return A string, containing the return string to send back over the control socket to the connected
	 *         client.
	 * @exception Exception Thrown if startScript fails.
	 * @see #doControlCommand
	 * @see #startScript
	 */
	protected String doGammaRayBurstAlertControlCommand(String args[]) throws Exception
	{
		SimpleDateFormat dateFormat = null;
		Date date = null;
		int intValue;
		double doubleValue;

		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		// acquire lock on alert data
		synchronized(alertDataLock)
		{
			alertData = new GCNDatagramAlertData();
			// Set notice date to now. Note this should really be set to pkt_sod,
			// but this won't work if the notice is sent around midnight.
			alertData.setNoticeDate(new Date());
			for(int i = 1; i < args.length; i++)
			{
				if(args[i].equals("-ra"))
				{
					if((i+1) < args.length)
					{
						try
						{
							RA ra = null;

							// Note this currently allows blank RA strings,
							// the next switch is read instead, and parseColon
							// doesn't seem to fail on -dec or whatever
							// You get a RA of 00:00:00
							ra = new RA();
							ra.parseColon(args[i+1]);
							alertData.setRA(ra);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
									  "Parsing RA:"+args[i+1]+" failed:"+e+".\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
								  "-ra requires a string argument.\n");
					}
				}
				else if(args[i].equals("-dec"))
				{
					if((i+1) < args.length)
					{
						try
						{
							Dec dec = null;

							// Note this currently allows blank Dec strings,
							// the next switch is read instead, and parseColon
							// doesn't seem to fail on -epoch or whatever
							// You get a Dec of +00:00:00
							dec = new Dec();
							dec.parseColon(args[i+1]);
							alertData.setDec(dec);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
									  "Parsing Dec:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
								  "-dec requires a string argument.\n");
					}
				}
				else if(args[i].equals("-epoch"))
				{
					if((i+1) < args.length)
					{
						try
						{
							doubleValue = Double.parseDouble(args[i+1]);
							alertData.setEpoch(doubleValue);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
								   "Parsing epoch:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
								  "-epoch requires a double argument.\n");
					}
				}
				else if(args[i].equals("-error_box"))
				{
					if((i+1) < args.length)
					{
						try
						{
							// error box is a radius in decimal arc-minutes
							doubleValue = Double.parseDouble(args[i+1]);
							alertData.setErrorBoxSize(doubleValue);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
								   "Parsing error box:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
								  "-error box requires a double argument.\n");
					}
				}
				else if(args[i].equals("-trigger_number"))
				{
					if((i+1) < args.length)
					{
						try
						{
							intValue = Integer.parseInt(args[i+1]);
							alertData.setTriggerNumber(intValue);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
		       					   "Parsing trigger number:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
								  "-trigger_number requires an integer argument.\n");
					}
				}
				else if(args[i].equals("-sequence_number"))
				{
					if((i+1) < args.length)
					{
						try
						{
							intValue = Integer.parseInt(args[i+1]);
							alertData.setSequenceNumber(intValue);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
		       					   "Parsing sequence number:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
								  "-sequence_number requires an integer argument.\n");
					}
				}
				else if(args[i].equals("-HETE"))
				{
					alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_HETE);
				}
				else if(args[i].equals("-INTEGRAL"))
				{
					alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_INTEGRAL);
				}
				else if(args[i].equals("-SWIFT"))
				{
					alertData.setAlertType(GCNDatagramAlertData.ALERT_TYPE_SWIFT);
				}
				else if(args[i].equals("-test"))
				{
					alertData.setTest(true);
				}
				else if(args[i].equals("-notice_date"))
				{
					if((i+1) < args.length)
					{
						try
						{
							date = dateFormat.parse(args[i+1]);
							alertData.setNoticeDate(date);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
		       					   "Parsing notice date:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
					"-notice_date requires an argument of the form yyyy-MM-dd'T'HH:mm:ss.\n");
					}
				}
				else if(args[i].equals("-grb_date"))
				{
					if((i+1) < args.length)
					{
						try
						{
							date = dateFormat.parse(args[i+1]);
							alertData.setGRBDate(date);
						}
						catch(Exception e)
						{
							return new String("doGammaRayBurstAlertControlCommand:"+
		       					   "Parsing GRB date:"+args[i+1]+" failed:"+e+"\n");
						}
						i++;
					}
					else
					{
						return new String("doGammaRayBurstAlertControlCommand:"+
					"-grb_date requires an argument of the form yyyy-MM-dd'T'HH:mm:ss.\n");
					}
				}
				else
					return new String("doGammaRayBurstAlertControlCommand:"+
							  "Recieved unknown command argument:"+args[i]+".\n");
			}
		}
		if(enableManualAlerts == false)
		{
			logger.log("Failed to start script. "+
				   "Manual Socket alerts have been disabled from the control socket.\n");
			return new String("Failed to start script. Manual Socket alerts have been disabled from the control socket.\n");
		}
		// ensure RA filled in
		if(alertData.getRA() == null)
		{
			logger.log("doGammaRayBurstAlertControlCommand: RA was NULL.");
			return new String("doGammaRayBurstAlertControlCommand: RA was NULL.");
		}
		// ensure Dec filled in
		if(alertData.getDec() == null)
		{
			logger.log("doGammaRayBurstAlertControlCommand: Dec was NULL.");
			return new String("doGammaRayBurstAlertControlCommand: Dec was NULL.");
		}
		if(alertData.getAlertType() == 0)
		{
			logger.log("doGammaRayBurstAlertControlCommand: No alert type specified.");
			return new String("doGammaRayBurstAlertControlCommand: No alert type specified.");
		}
		// Actually try and start the script
		startScript();
		return new String("doGammaRayBurstAlertControlCommand: Script started.\n");
	}

	/**
	 * Argument parser.
	 * @param args The argument list.
	 * @see #setMulticastPort
	 * @see #setGroupAddress
	 * @see #setScript
	 * @see #setMaxErrorBox
	 * @see #setAllowedAlerts
	 * @see #addAllowedAlerts
	 * @see #swiftSolnStatusRejectMask
	 * @see #swiftSolnStatusAcceptMask
	 * @see #controlServerPort
	 * @see #enableSocketAlerts
	 * @see #enableManualAlerts
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
			else if(args[i].equals("-control_port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						intValue = Integer.parseInt(args[i+1]);
						controlServerPort = intValue;
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing Control Port:"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-control_port requires a number.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-disable_manual_alerts"))
			{
				enableManualAlerts = false;
			}
			else if(args[i].equals("-disable_socket_alerts"))
			{
				enableSocketAlerts = false;
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
			else if(args[i].equals("-multicast_port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						intValue = Integer.parseInt(args[i+1]);
						setMulticastPort(intValue);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing Multicast Port:"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-multicast_port requires a number.");
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
			else if(args[i].equals("-sssam")||args[i].equals("-swift_soln_status_accept_mask"))
			{
				if((i+1) < args.length)
				{
					try
					{
						// if specified as hex
						if(args[i+1].startsWith("0x"))
						{
							// parse as hex, removing "0x"
							intValue = Integer.parseInt(args[i+1].substring(2),16);
						}
						else
							intValue = Integer.parseInt(args[i+1]);
						setSwiftSolnStatusAcceptMask(intValue);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing accept mask :"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-sssrm requires a integer mask.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-sssrm")||args[i].equals("-swift_soln_status_reject_mask"))
			{
				if((i+1) < args.length)
				{
					try
					{
						// if specified as hex
						if(args[i+1].startsWith("0x"))
						{
							// parse as hex, removing "0x"
							intValue = Integer.parseInt(args[i+1].substring(2),16);
						}
						else
							intValue = Integer.parseInt(args[i+1]);
						setSwiftSolnStatusRejectMask(intValue);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramScriptStarter:Parsing reject mask :"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramScriptStarter:-sssrm requires a integer mask.");
					System.exit(4);
				}
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
	 * @see #DEFAULT_CONTROL_PORT
	 */
	protected void help()
	{

		System.out.println("GCNDatagramScriptStarter Help");
		System.out.println("java -Dhttp.proxyHost=wwwcache.livjm.ac.uk "+
				   "-Dhttp.proxyPort=8080 GCNDatagramScriptStarter \n"+
				   "\t[-multicast_port <n>][-group_address <address>]"+
				   "\t[-control_port <n>][-disable_manual_alerts][-disable_socket_alerts]"+
				   "\t[-script <filename>][-all][-hete][-integral][-swift]\n"+
				   "\t[-max_error_box|-meb <arcsecs>]"+
				   "\t[-swift_soln_status_accept_mask|-sssam <bit mask>]"+
				   "\t[-swift_soln_status_reject_mask|-sssrm <bit mask>]");
		System.out.println("-script specifies the script/program to call on a successful alert.");
		System.out.println("-all specifies to call the script for all types of alerts.");
		System.out.println("-control_port specifies the port the control server sits on.");
		System.out.println("-disable_manual_alerts does not call the script when an alert is requested from the control socket.");
		System.out.println("-disable_socket_alerts does not call the script when an alert is generated from the multicast socket.");
		System.out.println("-hete specifies to call the script for HETE alerts.");
		System.out.println("-integral specifies to call the script for INTEGRAL alerts.");
		System.out.println("-swift specifies to call the script for SWIFT alerts.");
		System.out.println("-max_error_box means only call the script when the error box (radius) is less than that size.");
		System.out.println("-sssam sets the Swift solnStatus bits that MUST be present for the script to be started.");
		System.out.println("-sssrm sets the Swift solnStatus bits that MUST NOT be present for the script to be started.");
		System.out.println("-sssam and -sssrm can be specified in hexidecimal using the '0x' prefix.");
		System.out.println("The default control port number is "+DEFAULT_CONTROL_PORT+".");
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

		/**
		 * Constructor.
		 * @param p The process to monitor.
		 */
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
		/**
		 * The stream we are reading from.
		 */
		InputStream inputStream = null;
		/**
		 * The process we are monitoring.
		 */
		Process process = null;
		/**
		 * The name of the stream we are monitoring.
		 */
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

		/**
		 * Run method for this thread.
		 * <ul>
		 * <li>This creates a buffered input stream around the input stream.
		 * <li>It enters a loop until the stream returns EOF:
		 *     <ul>
		 *     <li>Bytes are read from the buffered input stream.
		 *     <li>The bytes are appeanded to a string buffer.
		 *     <li>If a newline character is encountered, the string buffer is logged to the logger,
		 *         and the string buffer reset.
		 *     </ul>
		 * </ul>
		 * @see #inputStream
		 * @see #streamName
		 * @see GCNDatagramScriptStarter#logger
		 */
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

	/**
	 * Inner class to run a control server.
	 */
	public class ControlServerThread implements Runnable
	{
		/**
		 * The port the control server is running on.
		 */
		protected int portNumber = 0;
		/**
		 * Set to true to stop the thread.
		 */
		protected boolean quit = false;
		/**
		 * The server socket.
		 */
		protected ServerSocket serverSocket = null;

		/**
		 * Default constructor.
		 */
		public ControlServerThread()
		{
			super();
		}

		/**
		 * The run method.
		 * Sets up the server socket. Enter a while loop until quit is true.
		 * Gets a connection socket, and calls startConnectionThread to start a connection thread
		 * to handle the connection.
		 * @see #serverSocket
		 * @see #quit
		 * @see#startConnectionThread
		 */
		public void run()
		{
			Socket connectionSocket = null;

			try
			{
				// create server socket
				serverSocket = new ServerSocket(portNumber);
				while(quit == false)
				{
					try
					{
						connectionSocket = serverSocket.accept();
						startConnectionThread(connectionSocket);
					}
					catch(Exception e)
					{
						logger.error(this.getClass().getName()+":run:",e);
					}
				}
			}
			catch(Exception e)
			{
				logger.error(this.getClass().getName()+":run:server socket",e);
			}
		}

		/**
		 * Method to set the port number of the control server port.
		 * @param p The port number
		 * @see #portNumber
		 */
		public void setPort(int p)
		{
			portNumber = p;
		}

		/**
		 * Quit the server socket.
		 * Set quit to true, and close the server socket (if it exists).
		 * @exception IOException Can be thrown when closing the server socket.
		 * @see #quit
		 * @see #serverSocket
		 */
		public void quit() throws IOException
		{
			quit = true;
			if(serverSocket != null)
				serverSocket.close();
		}

		/**
		 * Start a connection thread using the specified socket for communication.
		 * @param s The socket to communicate over.
		 * @see GCNDatagramScriptStarter.ControlServerConnectionThread
		 */
		protected void startConnectionThread(Socket s)
		{
			ControlServerConnectionThread csct = null;
			Thread t = null;

			csct = new ControlServerConnectionThread();
			csct.setSocket(s);
			t = new Thread(csct);
			t.start();
		}
	}

	/**
	 * Class implementing a connection from the control server socket.
	 */
	public class ControlServerConnectionThread implements Runnable
	{
		/**
		 * The connection socket.
		 */
		protected Socket socket = null;
		/**
		 * Default constructor.
		 */
		public ControlServerConnectionThread()
		{
			super();
		}

		/**
		 * Method to set the connection socket.
		 * @param s The socket.
		 * @see #socket
		 */
		public void setSocket(Socket s)
		{
			socket = s;
		}

		/**
		 * Run method.
		 * <ul>
		 * <li>Gets an input reader (getInputReader).
		 * <li>Gets an output reader (getOutputWriter).
		 * <li>Reads in the command string.
		 * <li>Splits it by whitespace.
		 * <li>Passes the array of command and arguments to doControlCommand
		 * <li>Writes the returned string to the socket.
		 * <li>Flushes the writer.
		 * <li>Closes reader, writer and connection socket.
		 * </ul>
		 * This method contains the JDK v1.4 method call String.split.
		 * @since 1.4
		 * @see #getInputReader
		 * @see #getOutputWriter
		 * @see GCNDatagramScriptStarter#doControlCommand
		 */
		public void run()
		{
			BufferedReader reader = null;
			PrintWriter writer = null;
			String commandString = null;
			String[] commandArray = null;
			String returnString = null;

			try
			{
				reader = getInputReader();
				writer = getOutputWriter();
				writer.flush();
				commandString = reader.readLine();
				// This next line is JDK 1.4 only, regex split.
				// \s means split on whitespace
				commandArray = commandString.split("\\s");
				returnString = doControlCommand(commandArray);
				writer.print(returnString);
				writer.flush();
				writer.close();
				reader.close();
				socket.close();
				socket = null;
			}
			catch(Exception e)
			{
				logger.error(this.getClass().getName()+":run:",e);
			}

		}

		/**
		 * Get a buffered reader from the socket.
		 * @see #socket
		 */
		protected BufferedReader getInputReader() throws IOException
		{
			return new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}

		/**
		 * Get a buffered writer from the socket.
		 * @see #socket
		 */
		protected PrintWriter getOutputWriter() throws IOException
		{
			return new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
		}

	}
}
//
// $Log: not supported by cvs2svn $
// Revision 1.20  2005/03/02 12:49:19  cjm
// Added comment.
//
// Revision 1.19  2005/03/01 19:00:44  cjm
// Comment fixes.
//
// Revision 1.18  2005/03/01 18:56:29  cjm
// More comments.
// Added command control socket.
// doControlCommand is the top-level method for executing command control socket commands.
// Added enableSocketAlerts and enableManualAlerts to enable command control socket control of whether
// alerts specified from the command socket server or datagram socket actually cause a script firing to occur.
// Added ControlServerThread inner class.
// Added doGammaRayBurstAlertControlCommand that actually does a manual (control socket) firing of the script.
// Added ControlServerConnectionThread inner class.
//
// Revision 1.17  2005/02/17 17:07:06  cjm
// Added swiftSolnStatusAcceptMask and swiftSolnStatusRejectMask.
//
// Revision 1.16  2005/02/15 15:48:14  cjm
// Added Swift error box logging.
//
// Revision 1.15  2005/02/15 14:48:23  cjm
// Added HETE tests for bra and bdec of -999.999 degrees.
//
// Revision 1.14  2005/02/15 11:47:55  cjm
// Set alert type to 0 (i.e. don't start script) if packet parsing fails.
//
// Revision 1.13  2005/02/11 18:44:00  cjm
// Added new solnStatus bits.
//
// Revision 1.12  2005/02/11 13:35:54  cjm
// Fixed logger copy error.
//
// Revision 1.11  2005/02/11 12:24:56  cjm
// Added more solution status parsing for Swift BAT.
//
// Revision 1.10  2005/02/09 11:17:13  cjm
// Added type 43 / readHeteGroundAnalysis parsing.
//
// Revision 1.9  2005/01/31 11:46:35  cjm
// Fixed Swift trigger/sequence number.
// Added soln status bit 6 detection.
//
// Revision 1.8  2005/01/28 18:41:16  cjm
// Hete update packets now check that BURST_INVALID flag is NOT set,
// rather than the BURST_VALID flag IS set. i.e. Packets without
// BURST_INVALID or BURST_VALID flags set are assumed to be valid.
//
// Revision 1.7  2005/01/21 14:13:25  cjm
// Added integral spiacs loggingg.
//
// Revision 1.6  2005/01/20 19:21:50  cjm
// Second attempt at fix for Integral test alerts.
// Can't test Status flags & (1<<31) > 1, because of signedness of ints.
// Now trying Status flags & (1<<31) != 0.
//
// Revision 1.5  2005/01/20 15:35:55  cjm
// No longer set alert type for HETE_ALERT - it contains no alert position.
// Refined integral test notice test so it actually works - also added more testMpos prints
// for futher debugging.
//
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
