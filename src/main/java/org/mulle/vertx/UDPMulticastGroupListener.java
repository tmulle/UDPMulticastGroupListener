package org.mulle.vertx;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import me.mulle.utilities.network.NICUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used to listen on a UDP Multicast group and print out
 * what it receives
 * 
 * @author tmulle
 */
public class UDPMulticastGroupListener extends AbstractVerticle {

    private final static Logger LOG = LoggerFactory.getLogger(UDPMulticastGroupListener.class);

    private static String INTERFACE;
    private static String MULTICAST_GROUP;
    private static String LISTEN_ALL_INTERFACE;
    private static int LISTEN_PORT;
    private static IP_MODE MODE;

    
    // Which mode
    private static enum IP_MODE {
        IPv4, IPv6
    };

    /**
     * Holds the sockets
     */
    private List<DatagramSocket> sockets;
    

    public static void main(String[] args) {
        
        // Create Vertx
        Vertx vertx = Vertx.vertx();
        
        // Create the config retriever
        ConfigRetriever config = ConfigRetriever.create(vertx);
        
        // Create deployment options
        DeploymentOptions options = new DeploymentOptions();
        
        // Load the config
        config.getConfig()
                .onFailure(error -> LOG.error("Failed to load config, shutting down", error))
                .onComplete(jsonObject -> options.setConfig(jsonObject.result()));
                
        // Deploy the verticle
        vertx.deployVerticle(new UDPMulticastGroupListener(), options);
    }

    /**
     * Attempt to close the UDP sockets (not sure if this is needed)
     * but sometimes I get a "No Buffer Space Available"
     * 
     * @param stopPromise
     * @throws Exception 
     */
    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        sockets.forEach(DatagramSocket::close);
        stopPromise.complete();
    }

    /**
     * Start the verticle
     * 
     * @param startPromise
     * @throws Exception 
     */
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        
        // Setup the variables
        MODE = IP_MODE.valueOf(config().getString("net.ip_mode", "IPv6"));
        INTERFACE = config().getString("net.interface");
        LISTEN_PORT = config().getInteger("net.listen_port", 35057);

        // Which mode?
        switch (MODE) {
            case IPv4: {
                MULTICAST_GROUP = "224.0.0.224";
                LISTEN_ALL_INTERFACE = "0.0.0.0";
                break;
            }
            case IPv6: {
                MULTICAST_GROUP = "FF02::1";
                LISTEN_ALL_INTERFACE = "::";
                break;
            }
        }

        LOG.info("*** Starting Variables ***");
        LOG.info("Network Mode = {}", MODE);
        LOG.info("Network Interface = {}", INTERFACE);
        LOG.info("Network Listen All Interface = {}", LISTEN_ALL_INTERFACE);
        LOG.info("Network Multicast Group = {}", MULTICAST_GROUP);
        LOG.info("Network Port = {}", LISTEN_PORT);
        LOG.info("**************************");


        // Get all interfaces matching mode (IP6 or IP4)
        boolean allowIP4 = MODE == IP_MODE.IPv4;
        boolean allowIP6 = MODE == IP_MODE.IPv6;

        // For each discovered IP address based on config
        // create a new DatagramSocket and start it listening on the multicast group
        Set<InetAddress> allAllowedLocalAddresses = NICUtils.getAllAllowedLocalAddresses(allowIP4, allowIP6, INTERFACE);
        sockets = allAllowedLocalAddresses.stream()
                .map(addr -> buildSocket(startPromise, addr))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
        
        // Complete the promise
        startPromise.complete();
    }

    /**
     * Build the socket and set it up for listening for requests
     * 
     * @param startPromise
     * @param addr
     * @return 
     */
    private DatagramSocket buildSocket(Promise<Void> startPromise, InetAddress addr) {

        NetworkInterface iface;
        try {
            iface = NetworkInterface.getByInetAddress(addr);
        } catch (SocketException ex) {
            LOG.error("Couldn't get Interface for address", ex);
            return null;
        }

        // Create the options 
        DatagramSocketOptions options = new DatagramSocketOptions()
                .setReuseAddress(true)
                .setReusePort(true)
                .setMulticastNetworkInterface(iface.getName());

        // Enable IPv6?
        if (MODE == IP_MODE.IPv6) {
            options.setIpV6(true);
        }

        // Create the socket
        DatagramSocket socket = vertx.createDatagramSocket(options);
        setupDiscoveryListener(startPromise, socket, iface);
        
        return socket;
        
    }

    /**
     * Listen on the Multicast Group
     *
     * @param startPromise
     * @param socket
     */
    private void setupDiscoveryListener(Promise<Void> startPromise, DatagramSocket socket, NetworkInterface iface) {

        socket.listen(LISTEN_PORT, LISTEN_ALL_INTERFACE)
                .onSuccess(result -> {
                    result.listenMulticastGroup(MULTICAST_GROUP)
                            .onSuccess(v -> LOG.info("Listening for Multicast Messages on {} Group [{}] on local address ({}): {}", MODE, MULTICAST_GROUP, iface.getName(), result.localAddress()))
                            .onFailure(startPromise::fail);
                })
                .onFailure(startPromise::fail);

        // Print out the message
        socket.handler(this::handlePacket);
    }

    /**
     * Used to respond to discovery requests
     *
     * @param packet
     */
    private void handlePacket(DatagramPacket packet) {
        LOG.info("Received Packet from {} with data {}", packet.sender(), packet.data());
    }

}
