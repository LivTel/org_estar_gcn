// GCNDatagramForwarder.java
package org.estar.gcn;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.estar.log.*;

/**
 * T
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class GCNDatagramForwarder implements Runnable, GCNDatagramListener
{
// constants
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNDatagramForwarder.java,v 1.1 2004-06-30 17:42:42 cjm Exp $";
	/**
	 * Length of a GCN socket packet, consisting of 40 long (int) words.
	 */
	public final static int PACKET_LENGTH = (40*4);
	/**
	 * Datagram Thread.
	 * @see GCNDatagramThread
	 */
	protected GCNDatagramThread datagramThread = null;
	/**
	 * Logger.
	 */
	protected ILogger logger = null;
	/**
	 * List of TCP/IP connection to forward any received datagram packets to.
	 * @see #GCNTCPConnection
	 */
	protected List forwardList = null;


	/**
	 * Default constructor.
	 * @see #datagramThread
	 * @see #forwardList
	 */
	public GCNDatagramForwarder() throws UnknownHostException, IOException
	{
		super();
		// create logger, must be created BEFORE datagramThread.addLogger
		logger = new GCNLogger();
		// add datagram thread
		datagramThread = new GCNDatagramThread();
		datagramThread.addListener(this);
		datagramThread.addLogger(logger);
		forwardList = new Vector();
	}

	/**
	 * Set the logger. Must be set otherwise the program will crash.
	 * @param l The logger to use.
	 * @see #logger
	 */
	public void setLogger(ILogger l)
	{
		logger = l;
	}

	/**
	 * Add a TCP connection to forward datagram packets to.
	 * @see #forwardList
	 * @see #logger
	 */
	public void addTCPConnection(InetAddress forwardAddress,int forwardPortNumber)
	{
		GCNTCPConnectionThread tcpConnectionThread = null;

		tcpConnectionThread = new GCNTCPConnectionThread();
		tcpConnectionThread.setAddress(forwardAddress);
		tcpConnectionThread.setPort(forwardPortNumber);
		tcpConnectionThread.setLogger(logger);
		// add to forward list
		logger.log(this.getClass().getName()+":addTCPConnection:"+forwardAddress+":"+forwardPortNumber);
		forwardList.add(tcpConnectionThread);
	}

	/** 
	 * Process a packet received on the datagram thread.
	 * Adds packet to each TCP connection thread in forwardList
	 * @see #forwardList
	 * @see #logPacket
	 */
	public void processPacket(byte buff[])
	{
		GCNTCPConnectionThread tcpConnectionThread = null;

		logger.log(this.getClass().getName()+":processPacket:Start.");
		// Print some info about the packet
		logPacket(buff);
		// forward to TCP connections
		for(int i = 0; i < forwardList.size(); i++)
		{
			logger.log(this.getClass().getName()+":run:Adding packet to TCP Forwarding Thread "+i+".");
			tcpConnectionThread = (GCNTCPConnectionThread)(forwardList.get(i));
			synchronized( tcpConnectionThread )
			{
				// only add packet to connected sockets, to prevent packets backing up.
				if(tcpConnectionThread.isConnected())
				{
					tcpConnectionThread.addPacket(buff);
					// get connection thread to jump out of wait loop
					tcpConnectionThread.notify();
				}
			}
		}
		logger.log(this.getClass().getName()+":processPacket:End.");
	}

	/**
	 * Run method.
	 */
	public void run()
	{
		GCNTCPConnectionThread tcpConnectionThread = null;
		Thread thread = null;

		try
		{
			logger.log(this.getClass().getName()+":run:Start.");
			// start datagram thread
			logger.log(this.getClass().getName()+":run:Starting Datagram Thread.");
			thread = new Thread(datagramThread);
			thread.start();
			// start TCP forward threads.
			logger.log(this.getClass().getName()+":run:Starting "+forwardList.size()+" TCP Forwarding Threads.");
			for(int i = 0; i < forwardList.size(); i++)
			{
				logger.log(this.getClass().getName()+":run:Starting TCP Forwarding Thread "+i+".");
				tcpConnectionThread = (GCNTCPConnectionThread)(forwardList.get(i));
				thread = new Thread(tcpConnectionThread);
				thread.start();
			}
			// wait for TCP threads to terminate
			for(int i = 0; i < forwardList.size(); i++)
			{
				logger.log(this.getClass().getName()+":run:Waiting for TCP Forwarding Thread "+i+
					   " to terminate.");
				tcpConnectionThread = (GCNTCPConnectionThread)(forwardList.get(i));
				while(tcpConnectionThread.getQuit() == false)
				{
					try
					{
						Thread.sleep(60000);
					}
					catch(InterruptedException e)
					{
						logger.error(this.getClass().getName()+
							     ":run:Waiting for TCP Forwarding Thread "+i+
							     " to terminate failed.",e);
					}
				}
				logger.log(this.getClass().getName()+":run:Waiting for TCP Forwarding Thread "+i+
					   " terminated.");
			}
			logger.log(this.getClass().getName()+":run:End.");
		}// end try
		catch(Exception e)
		{
			logger.error(this.getClass().getName()+":run:Error.",e);
		}

	}

	/**
	 * Print some info about the packet for debugging purposes.
	 * @param buff The packet to log.
	 * @see #logType
	 */
	protected void logPacket(byte buff[])
	{
		ByteArrayInputStream bin = null;
		DataInputStream inputStream = null;

		try
		{
			// Create an input stream from the buffer.
			bin = new ByteArrayInputStream(buff, 0,buff.length);
			inputStream = new DataInputStream(bin);
			logType(inputStream);
			inputStream.close();
			bin.close();
		}
		catch(Exception e)
		{
			logger.error(this.getClass().getName()+":logPacket:failed: ",e);
			return;
		}
	}

	/**
	 * Print type details.
	 * @param inputStream Data input stream scanning over packet buffer.
	 */
	protected void logType(DataInputStream inputStream) throws IOException
	{
		int type;

		type = inputStream.readInt();
		switch (type)
		{
		    case 3: 
			    logger.log(" [IMALIVE]");
			    break;
		    case 4:
			    logger.log(" [KILL]");
			    break;
		    case 34:  
			    logger.log(" [SAX/WFC_GRB_POS]");
			    break;
		    case 40: 
			    logger.log(" [HETE_ALERT]");
			    break;
		    case 41:
			    logger.log(" [HETE_UPDATE]");
			    break; 
		    case 51:
			    logger.log(" [INTEGRAL_POINTDIR]");
			    break;
		    case 53:
			    logger.log(" [INTEGRAL_WAKEUP]");
			    break;
		    case 54:
			    logger.log(" [INTEGRAL_REFINED]");
			    break;
		    case 55:
			    logger.log(" [INTEGRAL_OFFLINE]");
			    break;
		    default:
			    logger.log(" [TYPE-"+type+"]");
		}
	}

	// static main
	/**
	 * Main program, for testing GCNDatagramForwarder.
	 */
	public static void main(String[] args)
	{
		GCNDatagramForwarder gdf = null;
		GCNLogger logger = null;
		int forwardPortNumber = 0;
		InetAddress forwardAddress = null;

		// initialise instance
		try
		{
			gdf = new GCNDatagramForwarder();
			logger = new GCNLogger();
			gdf.setLogger(logger);
		}
		catch(Exception e)
		{
			System.err.println("Initialising GCNDatagramForwarder failed:"+e);
			e.printStackTrace(System.err);
			System.exit(2);
		}
		// parse arguments
		for(int i = 0; i < args.length; i++)
		{
			if(args[i].equals("-datagram_address"))
			{
				if((i+1) < args.length)
				{
					try
					{
						InetAddress address = null;

						address = InetAddress.getByName(args[i+1]);
						gdf.datagramThread.setGroupAddress(address);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramForwarder:Parsing Address:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(5);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramForwarder:-datagram_address requires an address.");
					System.exit(6);
				}
			}
			else if(args[i].equals("-datagram_port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						int portNumber;

						portNumber = Integer.parseInt(args[i+1]);
						gdf.datagramThread.setPort(portNumber);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramForwarder:Parsing Port:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
				}
				else
				{
					System.err.println("GCNDatagramForwarder:-datagram_port requires a number.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-forward_address"))
			{
				if((i+1) < args.length)
				{
					try
					{
						forwardAddress = InetAddress.getByName(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramForwarder:Parsing Forward Address:"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(5);
					}
					i++;
					// if complete spec, add forward
					if((forwardAddress != null)&&(forwardPortNumber > 0))
					{
						gdf.addTCPConnection(forwardAddress,forwardPortNumber);
						forwardAddress = null;
						forwardPortNumber = 0;
					}
				}
				else
				{
					System.err.println("GCNDatagramForwarder:-forward_address requires an address.");
					System.exit(6);
				}
			}
			else if(args[i].equals("-forward_port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						forwardPortNumber = Integer.parseInt(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNDatagramForwarder:Parsing forward Port:"+
								   args[i+1]+" failed:"+e);
						e.printStackTrace(System.err);
						System.exit(3);
					}
					i++;
					// if complete spec, add forward
					if((forwardAddress != null)&&(forwardPortNumber > 0))
					{
						gdf.addTCPConnection(forwardAddress,forwardPortNumber);
						forwardAddress = null;
						forwardPortNumber = 0;
					}
				}
				else
				{
					System.err.println("GCNDatagramForwarder:-forward_port requires a number.");
					System.exit(4);
				}
			}
			else if(args[i].equals("-help"))
			{
				System.out.println("GCNDatagramForwarder Help");
				System.out.println("java -Dhttp.proxyHost=wwwcache.livjm.ac.uk "+
				       "-Dhttp.proxyPort=8080 GCNDatagramForwarder "+
						   "\n\t[[-forward_port <n>][-forward_address <address>]...]"+
						   "\n\t[-datagram_port <n>][-datagram_address <address>]");
				System.exit(0);
			}
		}// end for
		// run forwarder thread
		try
		{
			gdf.run();
		}
		catch(Exception e)
		{
			System.err.println("GCNDatagramForwarder:Returned an error:"+e);
			e.printStackTrace();
		}
		System.exit(0);
	}

	/**
	 * Class containing information on a TCP/IP Connection to forward received GCN datagrams to.
	 */
	protected class GCNTCPConnectionThread implements Runnable
	{
		/**
		 * List of delay length's for re-connection attempts, in milliseconds.
		 * Taken from: http://gcn.gsfc.nasa.gov/tech_describe.html#tc17
		 */
		protected int delayLengthList[] = {0,60000,120000,240000,480000,960000,1800000};
		/**
		 * The port number to connect to.
		 */
		protected int portNumber;
		/**
		 * The address of the remote end of the TCP/IP connection.
		 */
		protected InetAddress address = null;
		/**
		 * The opened socket.
		 */
		protected Socket socket = null;
		/**
		 * The output stream to forward packets to the remote socket server.
		 */
		protected OutputStream outputStream = null;
		/**
		 * The input stream to receive packets back from the remote socket server.
		 */
		protected InputStream inputStream = null;
		/**
		 * Whether to quit the connection.
		 */
		protected boolean quit = false;
		/**
		 * Is the connection to the remote system open?
		 */
		protected boolean connectionOpen = false;
		/**
		 * Which connection attempt we are on. Used as an index into delayLengthList.
		 * @see #delayLengthList
		 */
		protected int connectionAttempt = 0;
		/**
		 * List of packets to forward.
		 */
		protected List packetList = null;
		/**
		 * Logger for this class.
		 */
		protected ILogger logger = null;

		/**
		 * Default constructor.
		 * @see #packetList
		 */
		public GCNTCPConnectionThread()
		{
			super();
			packetList = new Vector();
		}

		/**
		 * Set forward port number. Must be set before run.
		 */
		public void setPort(int p)
		{
			portNumber = p;
		}

		/**
		 * Set forward address. Must be set before run.
		 */
		public void setAddress(InetAddress a)
		{
			address = a;
		}

		public void setLogger(ILogger l)
		{
			logger = l;
		}

		public void quit()
		{
			quit = true;
		}

		public boolean getQuit()
		{
			return quit;
		}

		/**
		 * Return whether the thread is connected to the remote TCP server.
		 * @return True if the socket is connected, false if it is not.
		 * @see #connectionOpen
		 */
		public boolean isConnected()
		{
			return connectionOpen;
		}

		/**
		 * Add a packet to the list.
		 * @see #packetList
		 */
		public void addPacket(byte[] buff)
		{
			synchronized (packetList)
			{
				packetList.add(buff);
			}
		}

		/**
		 * Run method.
		 */
		public void run()
		{
			if(address == null)
			{
				logger.error(this.getClass().getName()+":"+address+":"+portNumber+
					     ":run:Address was null.");
				return;
			}
			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				     ":run:Entering main loop.");
			connectionAttempt = 0;
			while(quit == false)
			{
				logger.log(this.getClass().getName()+":"+address+":"+portNumber+
					     ":run:top of main loop.");
				try
				{
					openDelay();
					openConnection();
					while((quit == false)&&(connectionOpen == true))
					{
						logger.log(this.getClass().getName()+":"+address+":"+portNumber+
							   ":run:about to wait.");
						synchronized(this)
						{
							try
							{
								wait();
							}
							catch(InterruptedException e)
							{
								logger.error(this.getClass().getName()+":"+address+
									     ":"+portNumber+":run:wait error",e);
							}
						}
						logger.log(this.getClass().getName()+":"+address+":"+portNumber+
							   ":run:notified.");
						forwardPacket();
					}// end while not quit and connected
					closeConnection();
				}
				catch(Exception e )
				{
					logger.error(this.getClass().getName()+":"+address+":"+portNumber+
						   ":run:main loop error",e);
				}
			}// end while not quit
			logger.error(this.getClass().getName()+":"+address+":"+portNumber+
				     ":run:Main loop finished.");
		} // end run

		/**
		 * Delay the thread for a certain time, before trying to open a connection.
		 * @see #connectionAttempt
		 * @see #delayLengthList
		 */
		protected void openDelay()
		{
			int delayLength;

			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":openDelay:Calculating delay for connection attempt "+connectionAttempt+".");
			try
			{
				if(connectionAttempt < delayLengthList.length)
					delayLength = delayLengthList[connectionAttempt];
				else
					delayLength = delayLengthList[delayLengthList.length-1];
				logger.log(this.getClass().getName()+":"+address+":"+portNumber+
					   ":openDelay:about to delay "+delayLength+" ms.");
				Thread.sleep(delayLength);
				connectionAttempt++;
			}
			catch(Exception e)
			{
				logger.error(this.getClass().getName()+":"+address+":"+portNumber+
					     ":openDelay:Error:",e);
			}
		}

		/**
		 * Try to open a connection to the remote server.
		 * @exception IOException Thrown if creating the socket fails.
		 * @exception SecurityException Thrown if creating the socket fails.
		 * @see #socket
		 * @see #address
		 * @see #portNumber
		 * @see #outputStream
		 * @see #inputStream
		 * @see #connectionOpen
		 * @see #connectionAttempt
		 */
		protected void openConnection() throws IOException, SecurityException
		{
			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":openConnection:opening connection.");
			socket = new Socket(address,portNumber);
			outputStream = socket.getOutputStream();
			inputStream = socket.getInputStream();
			connectionOpen = true;
			// reset connection attempt
			connectionAttempt = 0;
			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":openConnection:connection opened.");
		}

		/**
		 * Forward a recieved data packet.
		 * @see #socket
		 * @see #outputStream
		 * @see #PACKET_LENGTH
		 */
		protected void forwardPacket() throws IOException
		{
			byte[] buff = null;
			long startTime,endTime,roundTripTime;
			int bytesRead,totalBytesRead;

			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":forwardPacket:About to start forward "+packetList.size()+" packets.");
			while(packetList.size() > 0)
			{
				logger.log(this.getClass().getName()+":"+address+":"+portNumber+
					   ":forwardPacket:Waiting to access packet list.");
				synchronized (packetList)
				{
					buff = (byte[])(packetList.remove(0));
				}
				logger.log(this.getClass().getName()+":"+address+":"+portNumber+
					   ":forwardPacket:Writing packet of length "+buff.length+".");
				startTime = System.currentTimeMillis();
				outputStream.write(buff);
				outputStream.flush();
				// wait for socket to send reply back to round trip time RTT.
				logger.log(this.getClass().getName()+":"+address+":"+portNumber+
					   ":forwardPacket:Waiting for return packet.");
				// diddly read return
				totalBytesRead = 0;
				bytesRead = 0;
				while((totalBytesRead < PACKET_LENGTH)&&(bytesRead > -1))
				{
					bytesRead = inputStream.read(buff,0,PACKET_LENGTH);
					totalBytesRead += bytesRead;
					logger.log(this.getClass().getName()+":"+address+":"+portNumber+
						   ":forwardPacket:Read "+totalBytesRead+" bytes of return packet.");
				}
				endTime = System.currentTimeMillis();
				roundTripTime = endTime-startTime;
				logger.log(this.getClass().getName()+":"+address+":"+portNumber+
						   ":forwardPacket:Packet has RTT of "+roundTripTime+".");
			}// while while packets to forward
			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":forwardPacket:Packets forwarded.");
		}

		/**
		 * Close the connection to the socket.
		 * @see #socket
		 * @see #outputStream
		 * @see #connectionOpen
		 * @see #connectionAttempt
		 * @exception IOException Thrown if there is a socket close error.
		 */
		protected void closeConnection() throws IOException
		{
			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":closeConnection:Start.");
			socket.close();
			socket = null;
			outputStream = null;
			connectionOpen = false;
			// reset connection attempt
			connectionAttempt = 0;
			logger.log(this.getClass().getName()+":"+address+":"+portNumber+
				   ":closeConnection:Connection closed.");
		}
	}// end class GCNTCPConnectionThread
}
//
// $Log: not supported by cvs2svn $
//
