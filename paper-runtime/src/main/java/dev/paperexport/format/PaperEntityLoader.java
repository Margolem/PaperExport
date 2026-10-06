package dev.paperexport.format;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;
import static dev.paperexport.format.PaperEntityDefinition.*;

public final class PaperEntityLoader {
    public static final long MAX_ARCHIVE=32L*1024*1024,MAX_EXPANDED=96L*1024*1024,MAX_ENTRY=16L*1024*1024,MAX_JSON=2L*1024*1024;
    private final Gson gson=new Gson();
    public PaperEntityDefinition load(Path path) throws IOException {
        if(Files.size(path)>MAX_ARCHIVE)throw new IOException("Archive exceeds 32 MiB");
        Map<String,byte[]> files=new HashMap<>();Set<String> seen=new HashSet<>();long total=0;int count=0;
        try(ZipFile zip=new ZipFile(path.toFile(),StandardCharsets.UTF_8)){
            var entries=zip.entries();while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();if(entry.isDirectory())continue;
                String name=entry.getName();
                if(!PaperExportValidator.safePath(name)||!seen.add(name.toLowerCase(java.util.Locale.ROOT)))throw new IOException("Unsafe or duplicate archive path: "+name);
                if(!name.matches("(?:manifest\\.json|model/model\\.json|entity/entity\\.json|animations/[a-z0-9_.-]+\\.json|textures/[a-z0-9_.-]+\\.png|sounds/[a-z0-9_.-]+\\.ogg|resourcepack/pack\\.mcmeta|resourcepack/assets/[a-z0-9_./-]+\\.(?:json|png|ogg|mcmeta)|preview/[a-z0-9_.-]+\\.png|metadata/export\\.json)"))throw new IOException("Unexpected or executable content: "+name);
                if(++count>512||entry.getSize()<0||entry.getSize()>MAX_ENTRY||entry.getCompressedSize()<0||entry.getCompressedSize()>MAX_ARCHIVE)throw new IOException("Archive entry exceeds limits: "+name);
                total+=entry.getSize();if(total>MAX_EXPANDED)throw new IOException("Expanded archive exceeds 96 MiB");
                try(InputStream in=zip.getInputStream(entry)){byte[] data=in.readNBytes((int)MAX_ENTRY+1);if(data.length!=entry.getSize())throw new IOException("Archive entry size mismatch: "+name);CRC32 crc=new CRC32();crc.update(data);if(crc.getValue()!=entry.getCrc())throw new IOException("Corrupt ZIP CRC: "+name);files.put(name,data);}
            }
        }
        PaperEntityDefinition d=new PaperEntityDefinition();d.files=files;
        d.manifest=parse(files,"manifest.json",Manifest.class);
        if(d.manifest==null||!"model/model.json".equals(d.manifest.model)||!"entity/entity.json".equals(d.manifest.entity))throw new IOException("manifest.json: missing or invalid paths");
        d.model=parse(files,d.manifest.model,Model.class);d.entity=parse(files,d.manifest.entity,Entity.class);
        d.animations=new HashMap<>();if(d.manifest.animations==null)throw new IOException("manifest.json: missing animations");
        for(var a:d.manifest.animations.entrySet())d.animations.put(a.getKey(),parse(files,a.getValue(),Animation.class));
        try {PaperExportValidator.validate(d);validateAssets(d);}catch(IllegalArgumentException ex){throw new IOException(ex.getMessage(),ex);}
        return d;
    }
    private <T>T parse(Map<String,byte[]> files,String path,Class<T> type)throws IOException{
        byte[] data=files.get(path);if(data==null)throw new IOException("Missing "+path);if(data.length>MAX_JSON)throw new IOException("JSON too large: "+path);
        try{return gson.fromJson(JsonParser.parseString(new String(data,StandardCharsets.UTF_8)),type);}catch(RuntimeException ex){throw new IOException("Invalid JSON: "+path,ex);}
    }
    private static void validateAssets(PaperEntityDefinition d)throws IOException{
        if(!d.files.containsKey("resourcepack/pack.mcmeta"))throw new IOException("Missing resourcepack/pack.mcmeta");
        String[] id=d.manifest.id.split(":",2);
        String base="resourcepack/assets/"+id[0]+"/";
        for(String path:d.files.keySet())if(path.startsWith("resourcepack/assets/")&&!(path.startsWith(base+"items/paperexport/"+id[1]+"/")||path.startsWith(base+"models/entity/"+id[1]+"/")||path.startsWith(base+"textures/item/paperexport/"+id[1]+"/")||path.startsWith(base+"sounds/paperexport/"+id[1]+"/")||path.equals(base+"sounds.json")))throw new IOException("Unexpected resource-pack namespace or path: "+path);
        for(var e:d.manifest.textures.entrySet()){Texture t=e.getValue();byte[] png=d.files.get(t.path);if(png==null)throw new IOException("Missing texture "+t.path);
            if(png.length<24||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71||png[12]!=73||png[13]!=72||png[14]!=68||png[15]!=82)throw new IOException("Invalid PNG: "+t.path);
            int w=java.nio.ByteBuffer.wrap(png,16,4).getInt(),h=java.nio.ByteBuffer.wrap(png,20,4).getInt();if(w!=t.width||h!=t.height||w>2048||h>2048)throw new IOException("Texture dimensions differ: "+t.path);
        }
        for(String name:d.manifest.textures.keySet())if(!d.files.containsKey(base+"textures/item/paperexport/"+id[1]+"/"+name+".png"))throw new IOException("Missing resource-pack texture "+name);
        if(d.manifest.sounds!=null&&!d.manifest.sounds.isEmpty()){
            if(!d.files.containsKey(base+"sounds.json"))throw new IOException("Missing resource-pack sounds.json");
            for(var e:d.manifest.sounds.entrySet()){
                byte[] ogg=d.files.get(e.getValue().path);
                if(ogg==null||ogg.length<32||ogg.length>8*1024*1024||ogg[0]!='O'||ogg[1]!='g'||ogg[2]!='g'||ogg[3]!='S')throw new IOException("Invalid Ogg sound "+e.getKey());
                if(!d.files.containsKey(base+"sounds/paperexport/"+id[1]+"/"+e.getKey()+".ogg"))throw new IOException("Missing resource-pack sound "+e.getKey());
            }
        }
        for(Bone b:d.model.bones)if(!b.cubes.isEmpty()){
            if(!d.files.containsKey(base+"items/paperexport/"+id[1]+"/"+b.id+".json")||!d.files.containsKey(base+"models/entity/"+id[1]+"/"+b.id+".json"))throw new IOException("Missing item model assets for bone "+b.id);
        }
    }
}
