package io.antigen.core.unit;

import io.antigen.core.config.FaultCollection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FaultCollectionTest {

    @Test
    public void testEnumConstantsExist() {
        assertNotNull(FaultCollection.null_field);
        assertNotNull(FaultCollection.missing_field);
        assertNotNull(FaultCollection.invalid_value);
        assertNotNull(FaultCollection.http_method_change);
    }

    @Test
    public void testEnumValuesAreCorrectlyNamed() {
        assertEquals("null_field", FaultCollection.null_field.name());
        assertEquals("missing_field", FaultCollection.missing_field.name());
        assertEquals("invalid_value", FaultCollection.invalid_value.name());
        assertEquals("http_method_change", FaultCollection.http_method_change.name());
    }

    @Test
    public void testEnumValuesCount() {
        assertEquals(6, FaultCollection.values().length);
    }

    @Test
    public void testEnumValuesContainsAllConstants() {
        FaultCollection[] expectedValues = {
                FaultCollection.null_field,
                FaultCollection.missing_field,
                FaultCollection.empty_list,
                FaultCollection.empty_string,
                FaultCollection.invalid_value,
                FaultCollection.http_method_change
        };
        assertArrayEquals(expectedValues, FaultCollection.values());
    }
}
