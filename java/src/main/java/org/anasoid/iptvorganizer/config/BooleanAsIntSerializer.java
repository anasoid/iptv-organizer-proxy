package org.anasoid.iptvorganizer.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Custom Jackson serializer that converts Boolean to 0/1 for frontend compatibility
 * The React admin UI expects numeric boolean values (0 = false, 1 = true)
 */
public class BooleanAsIntSerializer extends JsonSerializer<Boolean> {

    @Override
    public void serialize(Boolean value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(value ? 1 : 0);
        }
    }
}
