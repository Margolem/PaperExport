package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import dev.paperexport.format.PaperEntityLoader;
import dev.paperexport.format.PaperExportValidator;
import dev.paperexport.rig.PaperAnimationController;
import dev.paperexport.rig.RigMath;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

public class FormatAndRigTest {
    private final Path fixture=Path.of("../tests/fixtures/minimal.paperexport");
    @Test void typescriptArchiveLoadsInJava()throws Exception{
        PaperEntityDefinition d=new PaperEntityLoader().load(fixture);
        assertEquals("fixture:rig",d.manifest.id);assertEquals(6,d.model.bones.size());assertEquals(2,d.model.hitboxes.size());assertEquals(5,d.animations.size());
        assertEquals("sounds/tone.ogg",d.manifest.sounds.get("tone").path);
        assertTrue(d.entity.boss.enabled);
    }
    @Test void hierarchyAppliesParentRotation()throws Exception{
        PaperEntityDefinition d=new PaperEntityLoader().load(fixture);
        Map<String,PaperAnimationController.Pose> poses=new HashMap<>();
        poses.put("body",new PaperAnimationController.Pose(new Vector3f(),new Quaternionf().rotateY((float)Math.PI/2),new Vector3f(1,1,1)));
        Map<String,Matrix4f> result=RigMath.worldMatrices(d.model.bones,poses,0,1);
        Vector3f head=result.get("head").transformPosition(new Vector3f());
        assertEquals(1.5f,head.y,0.0001f);
        Vector3f arm=result.get("right_arm").transformPosition(new Vector3f());
        assertEquals(0,arm.x,0.0001f);assertEquals(-5f/16,arm.z,0.0001f);
    }
    @Test void quaternionInterpolationIsHalfway() {
        PaperEntityDefinition.Key a=new PaperEntityDefinition.Key(),b=new PaperEntityDefinition.Key();a.time=0;a.value=new double[]{0,0,0};b.time=1;b.value=new double[]{0,180,0};
        Quaternionf q=PaperAnimationController.sampleRotation(List.of(a,b),.5);
        Vector3f rotated=q.transform(new Vector3f(1,0,0));
        assertEquals(0,rotated.x,.001f);assertEquals(1,Math.abs(rotated.z),.001f);
    }
    @Test void modelForwardMatchesVanillaYaw(){
        Vector3f forward=new Vector3f(0,0,-1);
        Vector3f south=new Matrix4f().rotateY(RigMath.rootYaw(0)).transformDirection(new Vector3f(forward));
        Vector3f west=new Matrix4f().rotateY(RigMath.rootYaw(90)).transformDirection(new Vector3f(forward));
        assertEquals(1,south.z,.0001f);assertEquals(-1,west.x,.0001f);
    }
    @Test void invalidHitboxBoneRejected()throws Exception{
        PaperEntityDefinition d=new PaperEntityLoader().load(fixture);
        d.model.hitboxes.getFirst().bone="missing";
        assertThrows(IllegalArgumentException.class,()->PaperExportValidator.validate(d));
    }
    @Test void traversalRejected()throws Exception{
        Path zip=Files.createTempFile("paperexport-bad-",".paperexport");try{
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry("../outside.json"));out.write("{}".getBytes());out.closeEntry();}
            assertThrows(IOException.class,()->new PaperEntityLoader().load(zip));
        }finally{Files.deleteIfExists(zip);}
    }
    @Test void duplicatePackPathsRejected()throws Exception{
        PaperEntityDefinition d=new PaperEntityLoader().load(fixture);Path tmp=Files.createTempFile("paperexport-pack-",".zip");try{assertThrows(IOException.class,()->new PaperResourcePackBuilder().build(List.of(d,d),tmp));}finally{Files.deleteIfExists(tmp);}
    }
    @Test void invalidManifestVersionRejected()throws Exception{
        PaperEntityDefinition d=new PaperEntityLoader().load(fixture);d.manifest.format_version=2;assertThrows(IllegalArgumentException.class,()->PaperExportValidator.validate(d));
    }
    @Test void soundsFromSameNamespaceMerge()throws Exception{
        PaperEntityDefinition first=new PaperEntityLoader().load(fixture);
        PaperEntityDefinition second=new PaperEntityDefinition();
        second.manifest=new PaperEntityDefinition.Manifest();
        second.manifest.id="fixture:second";
        second.manifest.sounds=Map.of("tone",new PaperEntityDefinition.Sound());
        second.files=Map.of("resourcepack/assets/fixture/sounds/paperexport/second/tone.ogg",first.files.get("sounds/tone.ogg"));
        Path output=Files.createTempFile("paperexport-sounds-",".zip");
        try{
            new PaperResourcePackBuilder().build(List.of(first,second),output);
            try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(output.toFile())){
                var soundMap=zip.getEntry("assets/fixture/sounds.json");assertNotNull(soundMap);
                String json=new String(zip.getInputStream(soundMap).readAllBytes());
                assertTrue(json.contains("paperexport.rig.tone"));
                assertTrue(json.contains("paperexport.second.tone"));
            }
        }finally{Files.deleteIfExists(output);}
    }
    @Test void packMetadataCoversVersionFamilies(){
        assertEquals(34,PaperVersion.of("1.21.1").packMajor());
        assertEquals(46,PaperVersion.of("1.21.4").packMajor());
        assertEquals(75,PaperVersion.of("1.21.11").packMajor());
        assertEquals(84,PaperVersion.of("26.1.2").packMajor());
        assertEquals(88,PaperVersion.of("26.2").packMajor());
        assertEquals(1,PaperVersion.of("26.3").packMinor());
        assertThrows(IllegalArgumentException.class,()->PaperVersion.of("26.4"));
    }
    @Test void legacyPackUsesCustomModelData()throws Exception{
        PaperEntityDefinition d=new PaperEntityLoader().load(fixture);
        Path output=Files.createTempFile("paperexport-legacy-",".zip");
        try{
            var result=new PaperResourcePackBuilder().build(List.of(d),output,PaperVersion.of("1.21.1"));
            assertEquals(6,result.legacyModelData().size());
            try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(output.toFile())){
                assertNotNull(zip.getEntry("assets/minecraft/models/item/paper.json"));
                assertNull(zip.getEntry("assets/fixture/items/paperexport/rig/head.json"));
                String paper=new String(zip.getInputStream(zip.getEntry("assets/minecraft/models/item/paper.json")).readAllBytes());
                assertTrue(paper.contains("custom_model_data"));
                String metadata=new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes());
                assertTrue(metadata.contains("\"pack_format\":34"));
            }
        }finally{Files.deleteIfExists(output);}
    }
}
