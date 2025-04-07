package com.zenith;

import com.zenith.util.ComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ComponentTest {

    @Test
    public void testQueueNewlineReplacement() {
        var component = ComponentSerializer.deserialize("{\"extra\":[{\"color\":\"gold\",\"extra\":[{\"bold\":true,\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://shop.2b2t.org/\"},\"text\":\"shop.2b2t.org\"}],\"text\":\"Position in queue: 351\\nYou can purchase priority queue status to join the server faster, visit \"}],\"text\":\"\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\\n\"}");

        var replaced = component.replaceText(b -> b
            .matchLiteral("\n\n")
            .replacement("")
        );
        String replacedStringResult = ComponentSerializer.serializePlain(replaced);
        assertFalse(replacedStringResult.contains("\n\n"));
    }
}
