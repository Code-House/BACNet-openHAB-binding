package org.openhab.binding.bacnet.internal;

import org.code_house.bacnet4j.wrapper.api.BacNetToJavaConverter;

import com.serotonin.bacnet4j.type.Encodable;

public class BypassConverter implements BacNetToJavaConverter<Encodable> {

    @Override
    public Encodable fromBacNet(Encodable datum) {
        return datum;
    }

}
