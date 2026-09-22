package dev.jsvro.spring;

import dev.jsvro.core.JsvroCodec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroHttpMessageConverterTest {
    record Person(String name, int age) {}

    static class ControllerShape {
        List<Person> persons() { return List.of(); }
    }

    @Test
    void onlyClaimsTheJsvroMediaType() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        var converter = new JsvroHttpMessageConverter(mapper, new JsvroCodec(mapper));
        Method method = ControllerShape.class.getDeclaredMethod("persons");
        Type returnType = method.getGenericReturnType();

        assertTrue(converter.canWrite(returnType, List.class, JsvroMediaType.APPLICATION_JSVRO));
        assertFalse(converter.canWrite(returnType, List.class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canWrite(returnType, List.class, MediaType.ALL));
        assertFalse(converter.canWrite(returnType, List.class, MediaType.parseMediaType("application/*")));
    }
}
