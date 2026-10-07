package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import dev.paperexport.format.PaperEntityLoader;
import dev.paperexport.format.PaperExportValidator;
import dev.paperexport.rig.PaperAnimationController;
import dev.paperexport.rig.PaperModelRenderer;
import dev.paperexport.rig.PaperMovementTracker;
import dev.paperexport.rig.RigMath;
import org.bukkit.Location;
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
    @Test void displayArrowUsesControllerYaw(){
        Location controller=new Location(null,2,3,4,90,25);
        Location display=PaperModelRenderer.displayLocation(controller);
        assertEquals(90,display.getYaw());assertEquals(0,display.getPitch());
        assertEquals(90,controller.getYaw());assertEquals(25,controller.getPitch());
        assertEquals(2,display.getX());assertEquals(3,display.getY());assertEquals(4,display.getZ());
    }
    @Test void itemDisplayClientRotationMatchesLogicalRigAndHitboxes(){
        for(float bodyYaw:new float[]{0,45,90,180,-90}){
            Matrix4f logical=new Matrix4f().rotateY(RigMath.rootYaw(bodyYaw)).translate(.25f,1.5f,.5f).rotateX(.3f).scale(2,.8f,1.5f);
            Matrix4f original=new Matrix4f(logical);
            for(float displayYaw:new float[]{bodyYaw,bodyYaw+35}){
                Matrix4f sent=PaperModelRenderer.itemDisplayMatrix(logical,displayYaw);
                // Minecraft applies display yaw before our matrix, then 180 degrees to the item.
                Matrix4f client=new Matrix4f().rotateY((float)Math.toRadians(-displayYaw)).mul(sent).rotateY((float)Math.PI);
                for(Vector3f point:new Vector3f[]{new Vector3f(),new Vector3f(1,2,-3)}){
                    Vector3f expected=original.transformPosition(new Vector3f(point));
                    Vector3f actual=client.transformPosition(point);
                    assertEquals(expected.x,actual.x,.0001f);assertEquals(expected.y,actual.y,.0001f);assertEquals(expected.z,actual.z,.0001f);
                }
                Vector3f front=new Matrix4f().rotateY((float)Math.toRadians(-displayYaw)).mul(PaperModelRenderer.itemDisplayMatrix(new Matrix4f().rotateY(RigMath.rootYaw(bodyYaw)),displayYaw)).rotateY((float)Math.PI).transformDirection(new Vector3f(0,0,-1));
                assertEquals(-Math.sin(Math.toRadians(bodyYaw)),front.x,.0001);
                assertEquals(Math.cos(Math.toRadians(bodyYaw)),front.z,.0001);
            }
            assertTrue(logical.equals(original,.00001f),"visual correction must leave the hitbox transform alone");
        }
    }
    @Test void walkingUsesHorizontalTravelAndStopsAfterShortDelay(){
        PaperMovementTracker movement=new PaperMovementTracker();
        assertFalse(movement.sample(0,0,2));
        assertFalse(movement.sample(.01,0,2));
        assertTrue(movement.sample(.08,0,2));
        assertTrue(movement.sample(0,0,2));
        assertFalse(movement.sample(0,0,4));
        assertFalse(movement.sample(12,0,2),"teleports must not start a walk cycle");
    }
    @Test void locomotionSwitchesImmediatelyAndOneShotFinishes()throws Exception{
        PaperEntityDefinition definition=new PaperEntityLoader().load(fixture);
        PaperAnimationController controller=new PaperAnimationController(definition);
        assertEquals("idle",controller.name());
        assertEquals("walk",advanceName(controller,definition.animations.get("idle").length,true));
        controller.play("attack");assertEquals("attack",controller.name());
        assertEquals("idle",advanceName(controller,definition.animations.get("attack").length+.1,false));
        controller.force("death");
        assertEquals("death",advanceName(controller,definition.animations.get("death").length+.1,true));
    }
    private static String advanceName(PaperAnimationController controller,double seconds,boolean moving){controller.advance(seconds,moving);return controller.name();}
    @Test void compoundBoneRotationUsesBlockbenchZyxOrder(){
        Quaternionf rotation=PaperAnimationController.quat(new double[]{30,45,60});
        Vector3f actual=rotation.transform(new Vector3f(0,0,-1));
        Vector3f expected=new Matrix4f().rotateZ((float)Math.toRadians(60)).rotateY((float)Math.toRadians(45)).rotateX((float)Math.toRadians(30)).transformDirection(new Vector3f(0,0,-1));
        assertEquals(expected.x,actual.x,.0001f);assertEquals(expected.y,actual.y,.0001f);assertEquals(expected.z,actual.z,.0001f);
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
                assertNotNull(zip.getEntry("pack.png"));
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
