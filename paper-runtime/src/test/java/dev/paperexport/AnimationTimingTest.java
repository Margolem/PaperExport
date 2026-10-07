package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import dev.paperexport.rig.PaperAnimationController;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTimingTest {
    private static PaperEntityDefinition.Animation animation(String name, String loop, int priority) {
        var a=new PaperEntityDefinition.Animation();a.name=name;a.length=1;a.loop=loop;a.priority=priority;a.tracks=List.of();
        a.events=List.of(event(0),event(.5),event(1));return a;
    }
    private static PaperEntityDefinition.Event event(double time) { var e=new PaperEntityDefinition.Event();e.time=time;return e; }
    private static PaperAnimationController controller(PaperEntityDefinition.Animation... animations) {
        var d=new PaperEntityDefinition();d.animations=new java.util.HashMap<>();for(var a:animations)d.animations.put(a.name,a);return new PaperAnimationController(d);
    }
    @Test void emitsStartAndEveryCrossedLoopInOrder() {
        var c=controller(animation("idle","loop",0));
        assertEquals(List.of(0.,.5,1.,0.,.5,1.,0.),c.advance(2,false).stream().map(e->e.time).toList());
        assertEquals(0,c.time());assertTrue(c.advance(0,false).isEmpty());
        assertEquals(List.of(.5),c.advance(.5,false).stream().map(e->e.time).toList());
    }
    @Test void heldEndDoesNotRepeatAndPriorityRejectionIsReported() {
        var c=controller(animation("attack","hold",60),animation("hurt","once",80));
        assertTrue(c.play("hurt"));assertFalse(c.play("attack"));assertFalse(c.play("missing"));
        c.force("attack");assertEquals(3,c.advance(1,false).size());assertTrue(c.advance(1,false).isEmpty());
    }
    @Test void stepChangesOnTheKeyNotAfterIt() {
        var a=new PaperEntityDefinition.Key();a.time=0;a.value=new double[]{0,0,0};a.interpolation="step";
        var b=new PaperEntityDefinition.Key();b.time=1;b.value=new double[]{0,90,0};
        assertEquals(0,PaperAnimationController.sample(List.of(a,b),.999,new Vector3f()).y);
        assertEquals(90,PaperAnimationController.sample(List.of(a,b),1,new Vector3f()).y);
        assertEquals(-1,PaperAnimationController.sampleRotation(List.of(a,b),1).transform(new Vector3f(1,0,0)).z,.0001);
    }
    @Test void rejectsInvalidTime() {
        var c=controller(animation("idle","loop",0));
        assertThrows(IllegalArgumentException.class,()->c.advance(Double.NaN,false));
        assertThrows(IllegalArgumentException.class,()->c.advance(-1,false));
    }
}
