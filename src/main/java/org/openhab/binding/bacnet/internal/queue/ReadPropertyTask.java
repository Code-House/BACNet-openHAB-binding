package org.openhab.binding.bacnet.internal.queue;

import org.code_house.bacnet4j.wrapper.api.BacNetClient;
import org.code_house.bacnet4j.wrapper.api.Property;
import org.openhab.binding.bacnet.internal.BypassConverter;
import org.openhab.binding.bacnet.internal.PropertyValueReceiver;

import com.serotonin.bacnet4j.type.Encodable;

public class ReadPropertyTask implements Runnable {

    private final BacNetClient client;
    private final Property property;
    private final PropertyValueReceiver<Encodable> receiver;

    public ReadPropertyTask(BacNetClient client, Property property, PropertyValueReceiver<Encodable> receiver) {
        this.client = client;
        this.property = property;
        this.receiver = receiver;

    }

    @Override
    public void run() {
        Encodable value = client.getPropertyValue(property, new BypassConverter());
        receiver.receiveProperty(property, value);

    }

}
