# UDPMulticastGroupListener
Little utility to listen on a UDP multicast group and print the data

## Code Setup

The code has some initial defaults that I setup, defaults are in `()'s`:

* MODE - Which mode are we in `IPv4` or `IPv6` (`IPv6`)
* INTERFACE - Which interface to use ie. eth0, en0, etc. (`null`) - All Interfaces
* LISTEN_PORT - Which port to listen on (`35056`)
* MULTICAST_GROUP - The IPv4 or IPv6 Multicast Group (`FF02::1`)
* LISTEN_ALL_INTERFACES - Listen address for all interfaces (`::`)

You can override the options via the following env variables:
* net.ip_mode - must be "IPv4" or "IPv6"..defaults to `IPv6`
* net.interface - defaults to `All Interfaces`
* net.listen_port - defaults to `35057`

You'll most likely want to override the `net.interface` to match your system.
