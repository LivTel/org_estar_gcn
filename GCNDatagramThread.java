// GCNDatagramThread.java
package org.estar.gcn;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.estar.astrometry.*;
import org.estar.log.*;

/**
 * This class is a Runnable, that sits on a MulticastSocket, waiting to be sent packets from
 * a program sitting on a GCN Bacodine socket. This program is sent a copy
 * of the alert packets in a data packet.
 * There is a main method to test this thread independantly.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class GCNDatagramThread implements Runnable, GCNDatagramListener, ILoggable
{
// constants
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNDatagramThread.java,v 1.1 2004-06-29 15:26:52 cjm Exp $";
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
	/**
	 * The buffer used to receive the packet.
	 */
	protected byte packetBuff[];
	/**
	 * A list of listener's to tell about any received packet.
	 */
	protected List listenerList = null;
	/**
	 * Logger instance.
	 */
	ILogger logger = null;

	/**
	 * Default constructor. Initialises groupAddress to default.
	 * @exception UnknownHostException Thrown if the default address is unknown
	 * @exception IOException Thrown if creating GCNLogger instance fails.
	 * @see #groupAddress
	 * @see #DEFAULT_GROUP_ADDRESS
	 */
	public GCNDatagramThread() throws UnknownHostException, IOException
	{
		super();
		groupAddress = InetAddress.getByName(DEFAULT_GROUP_ADDRESS);
		listenerList = new Vector();
		logger = new GCNLogger();
	}

	/**
	 * Run method.
	 * @see #quit
	 * @see #initSocket
	 * @see #receivePacket
	 * @see #packet
	 */
	public void run()
	{
		GCNDatagramListener listener = null;
		try
		{
			logger.log(this.getClass().getName()+":run:Started.");
			quit = false;
			initSocket();
			while(quit == false)
			{
				receivePacket();
				for(int i = 0; i < listenerList.size(); i ++)
				{
					try
					{
						listener = (GCNDatagramListener)(listenerList.get(i));
						listener.processPacket(packet.getData());
					}
					catch(Exception e)
					{
						logger.error(this.getClass().getName()+
							     ":run:processPacket:listener = "+listener,e);
					}
				}
			}
		}
		catch(Exception e)
		{
			logger.error(this.getClass().getName()+":run:",e);
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
	 * Add a listener.
	 * @param gdl A listener.
	 * @see #listenerList
	 * @see GCNDatagramListener
	 */
	public void addListener(GCNDatagramListener gdl)
	{
		listenerList.add(gdl);
	}

	public void addLogger(ILogger l)
	{
		logger = l;
	}

	public void deleteLogger(ILogger l)
	{
		try
		{
			logger = new GCNLogger();
		}
		catch(IOException e)
		{
			// diddly
		}
	}

	/**
	 * Process data in packet. Default GCNDatagramListener.
	 * @see #packet
	 * @see #inputStream
	 * @see #logger
	 * @see #readType
	 * @see #readImalive
	 * @see #readSax
	 * @see #readHeteAlert
	 * @see #readHeteUpdate
	 * @see #readIntegralPointing
	 * @see #readIntegralWakeup
	 * @see #readIntegralRefined
	 * @see #readIntegralOffline
	 */
	public void processPacket(byte buff[])
	{
		ByteArrayInputStream bin = null;
		int type;

		logger.log(this.getClass().getName()+":processPacket:Started.");
		// Create an input stream from the buffer.
		bin = new ByteArrayInputStream(buff, 0,buff.length);
		inputStream = new DataInputStream(bin);
		// Set notice date to now. Note this should really be set to pkt_sod,
		// but this won't work if the notice is sent around midnight.
		//alertData.setNoticeDate(new Date());
		// parse data
		// call any listeners with parsed data
		try
		{
			type = readType();
		}
		catch(IOException e)
		{
			logger.error(this.getClass().getName()+":processPacket:readType failed: ",e);
			return;
		}
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
			    logger.log(" [HETE_ALERT]");			
			    readHeteAlert();			   
			    break;
		    case 41:
			    logger.log(" [HETE_UPDATE]");
			    readHeteUpdate();
			    break; 
		    case 51:
			    logger.log(" [INTEGRAL_POINTDIR]");
			    readIntegralPointing();
			    break;
		    case 53:
			    logger.log(" [INTEGRAL_WAKEUP]");
			    readIntegralWakeup();
			    break;
		    case 54:
			    logger.log(" [INTEGRAL_REFINED]");
			    readIntegralRefined();
			    break;
		    case 55:
			    logger.log(" [INTEGRAL_OFFLINE]");
			    readIntegralOffline();
			    break;
		    default:
			    logger.log(" [TYPE-"+type+"]");
		}
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
    
    /** Read the SOD for date.*/
	protected void readSod() throws IOException
	{
		int sod = inputStream.readInt();
		logger.log("SOD: "+sod);
	}
    
    /** Read stuffing bytes. */
	protected void readStuff(int from, int to) throws IOException
	{
		for (int i = from; i <= to; i++)
		{
			inputStream.readInt();
		}
		logger.log("Skipped: "+from+" to "+to);
	}
    
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
			int burstTjd = inputStream.readInt(); // 5 - burst_tjd
			int burstSod = inputStream.readInt(); // 6 - burst_sod
			logger.log("Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			logger.log("Burst: TJD:"+burstTjd+" SOD: "+burstSod);
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			bra = inputStream.readInt(); // Burst RA (x10e4 degs). // 7 - burst_ra
			bdec = inputStream.readInt(); // Burst Dec (x10e4 degs). // 8 = burst_dec
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians((double)bra)/10000.0);
			dec.fromRadians(Math.toRadians((double)bdec)/10000.0);
			logger.log("Burst RA: "+ra);
			logger.log("Burst Dec: "+dec);
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
			readStuff(16, 35); // skipped the wx, sx error boxes for now! 
			logger.log("Skipped WXM and SXC error boxes for now !");
			int posFlags = inputStream.readInt(); // 36 - pos_flags
			logger.log("Pos Flags: 0x"+Integer.toHexString(posFlags));
			int validity = inputStream.readInt(); // 37 - validity flags.
			logger.log("Validity Flag: 0x"+Integer.toHexString(validity));
			if(validity == 0x00000001)
			{
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
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// Note the burst data is in apparent coordinates (current EPOCH)
			// They should really be converted to J2000 coordinates for the Meade.
			logger.log("Burst RA(apparent): "+ra);
			logger.log("Burst Dec(apparent): "+dec);
			int detFlags   =  inputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  inputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  inputStream.readInt(); // 11 burst error (arcsec)
			logger.log("Burst error: "+((double)burstError)+" arcsec.");
			readStuff(12, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.	 
		}
		catch  (Exception e)
		{
			logger.error("INTEGRAL Wakeup: Error reading: ",e);
		}
	}

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
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// Note the burst data is in apparent coordinates (current EPOCH)
			// They should really be converted to J2000 coordinates for the Meade.
			logger.log("Burst RA(apparent): "+ra);
			logger.log("Burst Dec(apparent): "+dec);
			int detFlags   =  inputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  inputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  inputStream.readInt(); // 11 burst error (arcsec)
			logger.log("Burst error: "+((double)burstError)+" arcsec.");
			readStuff(12, 38);// note replace this with more parsing later
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
			//TJD=12640 is 01 Jan 2003
			int burstTjd = inputStream.readInt(); // 5 Burst TJD.
			int burstSod = inputStream.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log("Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log("Burst Date: "+burstDate);
			int bra    = inputStream.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = inputStream.readInt(); // 8 Dec(-90..90)degrees *10000.
			ra = new RA();
			dec = new Dec();
			ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// Note the burst data is in apparent coordinates (current EPOCH)
			// They should really be converted to J2000 coordinates for the Meade.
			logger.log("Burst RA(apparent): "+ra);
			logger.log("Burst Dec(apparent): "+dec);
			int detFlags   =  inputStream.readInt(); // 9 detector Test Flags.
			logger.log("Detector Flags: ["+Integer.toHexString(detFlags).toUpperCase()+"]");
			int intensitySigma   =  inputStream.readInt(); // 10 burst intensity sigma * 100
			logger.log("Intensity Sigma: "+(((double)intensitySigma)/100.0)+".");
			int burstError   =  inputStream.readInt(); // 11 burst error (arcsec)
			logger.log("Burst error: "+((double)burstError)+" arcsec.");
			readStuff(12, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.	 
		}
		catch  (Exception e)
		{
			logger.error("INTEGRAL Offline: Error reading: ",e);
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

	// static main
	/**
	 * Main program, for testing GCNDatagramThread.
	 */
	public static void main(String[] args)
	{
		GCNDatagramThread gdt = null;
		GCNLogger glog = null;
		int intValue;

		// initialise instance
		try
		{
			gdt = new GCNDatagramThread();
		}
		catch(Exception e)
		{
			System.err.println("Initialising GCNDatagramThread failed:"+e);
			e.printStackTrace(System.err);
			System.exit(2);
		}
		// create logger, so gdt will log to stdout.
		try
		{
			glog = new GCNLogger();
		}
		catch(IOException e)
		{
			System.err.println("Initialising logger failed:"+e);
			e.printStackTrace(System.err);
			System.exit(2);
		}
		// parse arguments
		for(int i = 0; i < args.length; i++)
		{
			if(args[i].equals("-port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						intValue = Integer.parseInt(args[i+1]);
						gdt.setPort(intValue);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramThread:Parsing Port:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramThread:-port requires a number.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-group_address"))
			{
				if((i+1) < args.length)
				{
					try
					{
						InetAddress address = null;

						address = InetAddress.getByName(args[i+1]);
						gdt.setGroupAddress(address);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramThread:Parsing Address:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(5);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramThread:-address requires an address.");
					System.exit(6);
				}
			}
			else if(args[i].equals("-help"))
			{
				System.out.println("GCNDatagramThread Help");
				System.out.println("java -Dhttp.proxyHost=wwwcache.livjm.ac.uk "+
				       "-Dhttp.proxyPort=8080 GCNDatagramThread "+
						   "\n\t[-port <n>][-group_address <address>]");
				System.exit(0);
			}
		}// end for
		// add default listener/logger
		gdt.addListener(gdt);
		gdt.addLogger(glog);
		// run thread
		gdt.run();
		System.exit(0);
	}

}
//
// $Log: not supported by cvs2svn $
//
