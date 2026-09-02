package com.xjicloud.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.xjicloud.common.BusinessException;
import org.junit.jupiter.api.Test;

class ContentRangeTest {

    @Test
    void parsesInclusiveRange() {
        ContentRange range = ContentRange.parse("bytes 0-8388607/10000000");
        assertEquals(0, range.start());
        assertEquals(8_388_607, range.endInclusive());
        assertEquals(10_000_000, range.total());
        assertEquals(8_388_608, range.length());
    }

    @Test
    void rejectsInvalidHeader() {
        assertThrows(BusinessException.class, () -> ContentRange.parse("bytes=0-1"));
        assertThrows(BusinessException.class, () -> ContentRange.parse("bytes 10-1/20"));
        assertThrows(BusinessException.class, () -> ContentRange.parse("bytes 0-19/10"));
    }
}
