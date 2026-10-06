package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PaperResourcePackBuilder {
    public record Result(Path path,String sha1,int resources,Map<String,Integer> legacyModelData) {}
    public Result build(Collection<PaperEntityDefinition> definitions,Path target)throws IOException{
        return build(definitions,target,PaperVersion.of("1.21.11"));
    }
    public Result build(Collection<PaperEntityDefinition> definitions,Path target,PaperVersion version)throws IOException{
        Map<String,byte[]> assets=new TreeMap<>();
        Map<String,JsonObject> soundMaps=new TreeMap<>();
        Map<String,Integer> legacyModelData=new TreeMap<>();
        assets.put("pack.mcmeta",version.packMetadata());
        JsonArray overrides=new JsonArray();int nextModelData=100000;
        for(PaperEntityDefinition d:definitions.stream().sorted(java.util.Comparator.comparing(value->value.manifest.id)).toList()){
            String[] parts=d.manifest.id.split(":",2);
            if(version.legacyItemModels())for(var bone:d.model.bones)if(!bone.cubes.isEmpty()){
                int modelData=nextModelData++;
                legacyModelData.put(d.manifest.id+"/"+bone.id,modelData);
                JsonObject override=new JsonObject();JsonObject predicate=new JsonObject();
                predicate.addProperty("custom_model_data",modelData);override.add("predicate",predicate);
                override.addProperty("model",parts[0]+":entity/"+parts[1]+"/"+bone.id);overrides.add(override);
            }
            if(d.manifest.sounds!=null&&!d.manifest.sounds.isEmpty()){
                JsonObject map=soundMaps.computeIfAbsent(parts[0],ignored->new JsonObject());
                for(String sound:d.manifest.sounds.keySet()){
                    String key="paperexport."+parts[1]+"."+sound;
                    if(map.has(key))throw new IOException("Sound event conflict: "+parts[0]+":"+key);
                    JsonObject value=new JsonObject();JsonArray variants=new JsonArray();JsonObject variant=new JsonObject();
                    variant.addProperty("name",parts[0]+":paperexport/"+parts[1]+"/"+sound);variant.addProperty("stream",false);
                    variants.add(variant);value.add("sounds",variants);map.add(key,value);
                }
            }
            for(var e:d.files.entrySet()){
                if(!e.getKey().startsWith("resourcepack/assets/")||e.getKey().endsWith("/sounds.json"))continue;
                String name=e.getKey().substring("resourcepack/".length());
                if(version.legacyItemModels()&&name.matches("assets/[^/]+/items/paperexport/.+"))continue;
                if(assets.putIfAbsent(name,e.getValue())!=null)throw new IOException("Resource-pack conflict: "+name);
            }
        }
        if(version.legacyItemModels()){
            JsonObject paper=new JsonObject();paper.addProperty("parent","minecraft:item/generated");
            JsonObject textures=new JsonObject();textures.addProperty("layer0","minecraft:item/paper");paper.add("textures",textures);paper.add("overrides",overrides);
            assets.put("assets/minecraft/models/item/paper.json",paper.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        for(var entry:soundMaps.entrySet())assets.put("assets/"+entry.getKey()+"/sounds.json",entry.getValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"paperexport-pack-",".zip");
        try{try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(tmp))){for(var e:assets.entrySet()){ZipEntry z=new ZipEntry(e.getKey());z.setTime(0);out.putNextEntry(z);out.write(e.getValue());out.closeEntry();}}
            try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
        try{byte[] hash=MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(target));return new Result(target,java.util.HexFormat.of().formatHex(hash),assets.size()-1,Map.copyOf(legacyModelData));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
