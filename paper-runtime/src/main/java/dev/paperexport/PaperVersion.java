package dev.paperexport;

import java.util.Map;

/** Resource-pack and item-model behavior for released Minecraft/Paper versions. */
public record PaperVersion(String minecraft,int packMajor,int packMinor,boolean legacyItemModels) {
    private static final Map<String,Integer> PACKS=Map.ofEntries(
        Map.entry("1.21",34),Map.entry("1.21.1",34),Map.entry("1.21.2",42),Map.entry("1.21.3",42),
        Map.entry("1.21.4",46),Map.entry("1.21.5",55),Map.entry("1.21.6",63),Map.entry("1.21.7",64),
        Map.entry("1.21.8",64),Map.entry("1.21.9",69),Map.entry("1.21.10",69),Map.entry("1.21.11",75),
        Map.entry("26.1",84),Map.entry("26.1.1",84),Map.entry("26.1.2",84),
        Map.entry("26.2",88),Map.entry("26.3",97)
    );
    public static PaperVersion of(String minecraft){
        Integer pack=PACKS.get(minecraft);
        if(pack==null)throw new IllegalArgumentException("Unsupported Paper/Minecraft version "+minecraft+"; supported: 1.21 through 1.21.11 and 26.1 through 26.3");
        return new PaperVersion(minecraft,pack,"26.3".equals(minecraft)?1:0,pack<46);
    }
    public byte[] packMetadata(){
        String body=packMajor<65?
            "{\"pack\":{\"pack_format\":"+packMajor+",\"description\":\"PaperExport entities\"}}":
            "{\"pack\":{\"min_format\":["+packMajor+","+packMinor+"],\"max_format\":["+packMajor+","+packMinor+"],\"description\":\"PaperExport entities\"}}";
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
