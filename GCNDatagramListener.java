// GCNDatagramListener.java
package org.estar.gcn;

/**
 * This interface 
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public interface GCNDatagramListener
{
	/**
	 * Process the GCN packet.
	 * @param buff The buffer containing the packet data.
	 */
	public void processPacket(byte buff[]);
}
//
// $Log: not supported by cvs2svn $
//
