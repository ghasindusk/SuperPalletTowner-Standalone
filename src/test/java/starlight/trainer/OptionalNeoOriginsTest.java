package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** NeoOrigins is optional: only the bridge classes may refer to its API. */
class OptionalNeoOriginsTest {
    private static final Set<String> BRIDGES = Set.of("NeoOriginsBridge", "NeoOriginsClientBridge", "OriginCooldownAffinity");

    @Test
    void onlyBridgeClassesReferToNeoOrigins() throws IOException, URISyntaxException {
        Path dir = Path.of(TrainerOriginLogic.class.getResource("TrainerOriginLogic.class").toURI()).getParent();
        List<String> offenders;
        try (Stream<Path> files = Files.walk(dir)) {
            offenders = files.filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> !BRIDGES.contains(p.getFileName().toString().replaceAll("(\\$.*)?\\.class$", "")))
                    .filter(OptionalNeoOriginsTest::mentionsNeoOrigins)
                    .map(p -> dir.relativize(p).toString()).sorted().toList();
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void bridgesExist() {
        for (String name : BRIDGES) assertFalse(TrainerOriginLogic.class.getResource(name + ".class") == null, name);
    }

    private static boolean mentionsNeoOrigins(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains("com/cyberday1/neoorigins");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
