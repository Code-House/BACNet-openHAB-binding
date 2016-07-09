package org.openhab.binding.bacnet.internal;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.code_house.bacnet4j.wrapper.api.BacNetClient;
import org.code_house.bacnet4j.wrapper.api.BacNetClientException;
import org.code_house.bacnet4j.wrapper.api.Device;
import org.code_house.bacnet4j.wrapper.api.DeviceDiscoveryListener;
import org.code_house.bacnet4j.wrapper.api.JavaToBacNetConverter;
import org.code_house.bacnet4j.wrapper.api.Property;
import org.code_house.bacnet4j.wrapper.ip.BacNetIpClient;
import org.openhab.binding.bacnet.BacNetBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.UnDefType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.type.Encodable;

public class BacNetBinding extends AbstractActiveBinding<BacNetBindingProvider>
        implements ManagedService, DeviceDiscoveryListener {
    static final Logger logger = LoggerFactory.getLogger(BacNetBinding.class);

    private static final Integer DEFAULT_LOCAL_DEVICE_ID = 1339;

    private Map<Integer, Device> deviceMap = Collections.synchronizedMap(new HashMap<Integer, Device>());
    private HashMap<BacNetBindingConfig, Encodable> lastUpdate = new HashMap<BacNetBindingConfig, Encodable>();
    private IpNetworkBuilder networkConfigurationBuilder;
    private BacNetClient client;

    private Integer localDeviceId = DEFAULT_LOCAL_DEVICE_ID;

    @Override
    protected String getName() {
        return "BacNet Service";
    }

    @Override
    public void activate() {
        super.activate();
        logger.debug("Bacnet binding activated");
    }

    @Override
    public void deactivate() {
        super.deactivate();
        logger.debug("Bacnet binding is going down");
        if (client != null) {
            client.stop();
            client = null;
        }
    }

    @Override
    public void bindingChanged(BindingProvider provider, String itemName) {
        lastUpdate = new HashMap<BacNetBindingConfig, Encodable>();
    }

    @Override
    protected void internalReceiveUpdate(String itemName, State newState) {
        performUpdate(itemName, newState);
    }

    @Override
    public void internalReceiveCommand(String itemName, Command command) {
        performUpdate(itemName, command);
    }

    private void performUpdate(final String itemName, final Type newValue) {
        final BacNetBindingConfig config = configForItemName(itemName);
        if (config != null) {
            Property endpoint = deviceEndpointForConfig(config);
            if (endpoint != null) {
                try {
                    client.setPropertyValue(endpoint, newValue, new JavaToBacNetConverter<Type>() {
                        @Override
                        public Encodable toBacNet(Type java) {
                            return BacNetValueConverter.openHabTypeToBacNetValue(config.type.getBacNetType(), newValue);
                        }
                    });
                    lastUpdate.remove(config);
                } catch (BacNetClientException e) {
                    logger.error("Could not set value {} for property {} for item {} (bacnet {}:{})", newValue,
                            endpoint, config.itemName, e);
                }
            }
        }
    }

    public void addBindingProvider(BacNetBindingProvider provider) {
        super.addBindingProvider(provider);
    }

    public void removeBindingProvider(BacNetBindingProvider bindingProvider) {
        super.removeBindingProvider(bindingProvider);
    }

    @Override
    protected void execute() {
        for (BacNetBindingProvider provider : providers) {
            for (BacNetBindingConfig config : provider.allConfigs()) {
                Property property = deviceEndpointForConfig(config);
                if (property != null) {
                    try {
                        update(property);
                    } catch (BacNetClientException e) {
                        logger.error("Could not fetch property {} for item {} from bacnet", property, config.itemName,
                                e);
                    }
                }
            }
        }
    }

    @Override
    protected long getRefreshInterval() {
        return TimeUnit.SECONDS.toMillis(30);
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        deactivate();

        if (properties == null) {
            return;
        }

        this.networkConfigurationBuilder = new IpNetworkBuilder();
        if (properties.get("localBindAddress") != null) {
            networkConfigurationBuilder.localBindAddress((String) properties.get("localBindAddress"));
        }
        if (properties.get("broadcast") != null) {
            networkConfigurationBuilder.broadcastIp((String) properties.get("broadcast"));
        }
        if (properties.get("port") != null) {
            networkConfigurationBuilder.port(Integer.parseInt((String) properties.get("port")));
        }
        if (properties.get("localNetworkNumber") != null) {
            networkConfigurationBuilder
                    .localNetworkNumber(Integer.parseInt((String) properties.get("localNetworkNumber")));
        }

        if (properties.get("localDeviceId") != null) {
            this.localDeviceId = Integer.parseInt((String) properties.get("localDeviceId"));
        } else {
            if (this.localDeviceId != DEFAULT_LOCAL_DEVICE_ID) {
                localDeviceId = DEFAULT_LOCAL_DEVICE_ID; // reset to default from previous value
            }
        }

        client = new BacNetIpClient(networkConfigurationBuilder.build(), localDeviceId);
        client.start();
        setProperlyConfigured(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                client.discoverDevices(BacNetBinding.this, 5000);
            }
        }).start();
    }

    protected void update(Property property) {
        if (client == null) {
            logger.error("Ignoring update request for property {}, client is not ready yet", property);
            return;
        }
        Encodable value = client.getPropertyValue(property, new BypassConverter());
        State state = UnDefType.UNDEF;
        BacNetBindingConfig config = configForEndpoint(property);
        if (config == null || value == null) {
            return;
        }
        Encodable oldValue = lastUpdate.get(config);
        if (oldValue == null || !oldValue.equals(value)) {
            state = this.createState(config.itemType, value);
            eventPublisher.postUpdate(config.itemName, state);
            lastUpdate.put(config, value);
        } else {
            logger.trace("Ignoring read result {} for item {} cause property {} value didn't change since last time",
                    value, config.itemName, property);
        }
    }

    private State createState(Class<? extends Item> type, Encodable value) {
        try {
            return BacNetValueConverter.bacNetValueToOpenHabState(type, value);
        } catch (Exception e) {
            logger.debug("Couldn't create state of type '{}' for value '{}'", type, value);
            return StringType.valueOf(value.toString());
        }
    }

    private Property deviceEndpointForConfig(BacNetBindingConfig config) {
        Device device = deviceMap.get(config.deviceId);
        if (device != null) {
            return new Property(device, config.id, config.type);
        }
        return null;
    }

    private BacNetBindingConfig configForItemName(String itemName) {
        for (BacNetBindingProvider provider : providers) {
            BacNetBindingConfig config = provider.configForItemName(itemName);
            if (config != null) {
                return config;
            }
        }
        return null;
    }

    private BacNetBindingConfig configForEndpoint(Property property) {
        for (BacNetBindingProvider provider : providers) {
            BacNetBindingConfig config = provider.configForEndpoint(property.getDevice().getInstanceNumber(),
                    property.getType(), property.getId());
            if (config != null) {
                return config;
            }
        }
        return null;
    }

    @Override
    public void deviceDiscovered(Device device) {
        logger.info("Discovered device " + device);
        deviceMap.put(device.getInstanceNumber(), device);
    }
}
