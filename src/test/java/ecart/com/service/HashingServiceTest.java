package ecart.com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HashingServiceTest {

    private final HashingService hashingService = new HashingService();

    @Test
    void testSha256GeneratesCorrectHash() {
        String result = hashingService.sha256("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result);
    }

    @Test
    void testSha256EmptyInput() {
        String result = hashingService.sha256("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }
}
