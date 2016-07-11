package org.openhab.binding.bacnet.internal.queue;

import org.code_house.bacnet4j.wrapper.api.BacNetClient;
import org.code_house.bacnet4j.wrapper.api.BacNetClientException;
import org.code_house.bacnet4j.wrapper.api.JavaToBacNetConverter;
import org.code_house.bacnet4j.wrapper.api.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WritePropertyTask<T> implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(WritePropertyTask.class);

    private final BacNetClient client;
    private final Property property;
    private final T value;
    private final JavaToBacNetConverter<T> converter;

    public WritePropertyTask(BacNetClient client, Property property, T value, JavaToBacNetConverter<T> converter) {
        this.client = client;
        this.property = property;
        this.value = value;
        this.converter = converter;

    }

    @Override
    public void run() {
        try {
            client.setPropertyValue(property, value, converter);
        } catch (BacNetClientException e) {
            logger.error("Could not set value {} for property {}", value, property, e);
        }
    }

}
