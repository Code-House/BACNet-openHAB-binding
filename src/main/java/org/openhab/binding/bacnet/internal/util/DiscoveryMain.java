package org.openhab.binding.bacnet.internal.util;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.code_house.bacnet4j.wrapper.api.Device;
import org.code_house.bacnet4j.wrapper.api.Property;
import org.code_house.bacnet4j.wrapper.ip.BacNetIpClient;
import org.openhab.binding.bacnet.internal.BypassConverter;

public class DiscoveryMain {

    public static void main(String[] args) throws Exception {
        // For each interface ...
        System.out.println("Fetching network interfaces");
        List<String> interfaceIPs = new ArrayList<>();
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
            NetworkInterface networkInterface = en.nextElement();
            if (!networkInterface.isLoopback()) {

                // .. and for each address ...
                for (Iterator<InterfaceAddress> it = networkInterface.getInterfaceAddresses().iterator(); it
                        .hasNext();) {

                    // ... get IP and Subnet
                    InterfaceAddress interfaceAddress = it.next();

                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        interfaceIPs.add(broadcast.getHostAddress());
                    }
                }
            }
        }

        if (interfaceIPs.isEmpty()) {
            System.out.println("No broadcast interfaces found");
        }

        for (String broadcast : interfaceIPs) {
            System.out.println("Fetching devices for " + broadcast + " addres with 30 second timeout");
            BacNetIpClient client = new BacNetIpClient(broadcast, 1339);
            client.start();

            Set<Device> devices = client.discoverDevices(30000L);
            if (devices.isEmpty()) {
                System.out.println(" => No Devices found");
            } else {
                for (Device device : devices) {
                    System.out.println("  => Device id " + device.getInstanceNumber());
                    System.out.println("     Metadata");
                    System.out.println("       Address: " + device.getHostAddress() + ":" + device.getPort());
                    System.out.println("       Name: " + device.getName());
                    System.out.println("       Model: " + device.getModelName());
                    System.out.println("       Vendor: " + device.getVendorName());

                    List<Property> properties = client.getDeviceProperties(device);
                    if (properties.isEmpty()) {
                        System.out.println("      => No properties found");
                    } else {
                        System.out.println("      => Properties:");
                        for (Property property : client.getDeviceProperties(device)) {
                            System.out.println("          => Type " + property.getType().name() + " id: "
                                    + property.getId() + ", present value "
                                    + client.getPropertyValue(property, new BypassConverter()));
                            System.out.println("             Metadata");
                            System.out.println("               Name: " + property.getName());
                            System.out.println("               Units: " + property.getUnits());
                            System.out.println("               Description: " + property.getDescription());
                        }
                    }

                }
            }

            client.stop();
        }

        System.out.println("Discovery complete");
    }
}
