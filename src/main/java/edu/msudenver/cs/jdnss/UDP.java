package edu.msudenver.cs.jdnss;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.Logger;

/**
 * This class is used by UDP and for extended for MC queries.
 */
class UDP extends Thread
{
    DatagramSocket dsocket;
    final Logger logger = JDNSS.logger;

    // public UDP() {} // don't do anything, let MC() do all the work.

    public UDP() throws SocketException, UnknownHostException
    {
        try
        {
            jdnssArgs JdnssArgs = JDNSS.jargs;
            String ipaddress = JdnssArgs.IPaddress;
            int port = JdnssArgs.port;
            if (ipaddress != null)
            {
                dsocket = new DatagramSocket(port,
                    InetAddress.getByName(ipaddress));
            }
            else
            {
                dsocket = new DatagramSocket(port);
            }
        }
        catch (UnknownHostException | SocketException uhe)
        {
            logger.catching(uhe);
            throw uhe;
        }
    }

    /**
     * This method is used by both UDP and MC
     */
    public void run()
    {
        logger.traceEntry();

        /*
        "Multicast DNS Messages carried by UDP may be up to the IP MTU of the
        physical interface, less the space required for the IP header(20
        bytes for IPv4; 40 bytes for IPv6) and the UDP header(8 bytes)."

        Ergo, we should probably calculate those values here instead of
        just going with 1500.
        */

        int size = this instanceof MC ? 1500 : 512;

        byte[] buffer = new byte[size];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        int threadPoolSize = JDNSS.jargs.threads;
        ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);

        while (true)
        {
            try
            {
                dsocket.receive(packet);
            }
            catch (IOException ioe)
            {
                logger.catching(ioe);
                continue;
            }

            Future f = pool.submit(
                new UDPThread(
                    Utils.trimByteArray(packet.getData(), packet.getLength()),
                    dsocket, packet.getPort(), packet.getAddress()
                )
            );

            // if we're only supposed to answer once, and we're the first,
            // bring everything down with us.
            if (JDNSS.jargs.once)
            {   
                try 
                {   
                    f.get();
                }
                catch (InterruptedException | ExecutionException ie)
                {   
                    logger.catching(ie);
                }

                System.exit(0);
            }
        }
    }
}
