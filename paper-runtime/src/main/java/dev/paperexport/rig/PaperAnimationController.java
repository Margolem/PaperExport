package dev.paperexport.rig;

import dev.paperexport.format.PaperEntityDefinition;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import static dev.paperexport.format.PaperEntityDefinition.*;

public final class PaperAnimationController {
    public record Pose(Vector3f position,Quaternionf rotation,Vector3f scale) {}
    private final PaperEntityDefinition definition;
    private Animation current;
    private double time;
    public PaperAnimationController(PaperEntityDefinition definition){this.definition=definition;play("idle");}
    public String name(){return current==null?"none":current.name;}
    public double time(){return time;}
    public Animation current(){return current;}
    public void play(String name){Animation next=definition.animations.get(name);if(next==null)return;if(current==next&&"loop".equals(next.loop))return;if(current!=null&&current.priority>next.priority&&time<current.length&& !"loop".equals(current.loop))return;current=next;time=0;}
    public void force(String name){Animation next=definition.animations.get(name);if(next!=null){current=next;time=0;}}
    public List<Event> advance(double seconds,boolean moving){
        String locomotion=moving&&definition.animations.containsKey("walk")?"walk":"idle";
        if(current==null)play(locomotion);
        if(current==null)return List.of();
        if(current.name.equals("idle")||current.name.equals("walk")||current.name.equals("run")){
            if(!current.name.equals(locomotion))force(locomotion);
        }
        Animation old=current;double from=time;time+=seconds;List<Event> fired=new ArrayList<>();
        for(Event e:old.events)if(e.time>from&&e.time<=Math.min(time,old.length))fired.add(e);
        if(time>=current.length){if("loop".equals(current.loop)){time%=current.length;for(Event e:old.events)if(e.time<=time)fired.add(e);}else if("hold".equals(current.loop))time=current.length;else{current=null;time=0;play(locomotion);}}
        return fired;
    }
    public Pose pose(Bone bone){
        if(current==null)return new Pose(new Vector3f(),new Quaternionf(),new Vector3f(1,1,1));
        Track t=null;for(Track candidate:current.tracks)if(bone.id.equals(candidate.bone)){t=candidate;break;}
        if(t==null)return new Pose(new Vector3f(),new Quaternionf(),new Vector3f(1,1,1));
        return new Pose(sample(t.position,time,new Vector3f()),sampleRotation(t.rotation,time),sample(t.scale,time,new Vector3f(1,1,1)));
    }
    public static Vector3f sample(List<Key> keys,double time,Vector3f fallback){
        if(keys==null||keys.isEmpty())return new Vector3f(fallback);
        if(time<=keys.getFirst().time)return vec(keys.getFirst().value);
        for(int i=1;i<keys.size();i++)if(time<=keys.get(i).time){Key a=keys.get(i-1),b=keys.get(i);float f="step".equals(a.interpolation)?0:(float)((time-a.time)/(b.time-a.time));return vec(a.value).lerp(vec(b.value),f);}
        return vec(keys.getLast().value);
    }
    public static Quaternionf sampleRotation(List<Key> keys,double time){
        if(keys==null||keys.isEmpty())return new Quaternionf();
        if(time<=keys.getFirst().time)return quat(keys.getFirst().value);
        for(int i=1;i<keys.size();i++)if(time<=keys.get(i).time){Key a=keys.get(i-1),b=keys.get(i);float f="step".equals(a.interpolation)?0:(float)((time-a.time)/(b.time-a.time));return quat(a.value).slerp(quat(b.value),f);}
        return quat(keys.getLast().value);
    }
    private static Vector3f vec(double[] v){return new Vector3f((float)v[0],(float)v[1],(float)v[2]);}
    /** Blockbench's ZYX Euler order composes Rz * Ry * Rx. */
    public static Quaternionf quat(double[] degrees){return new Quaternionf().rotationZYX((float)Math.toRadians(degrees[2]),(float)Math.toRadians(degrees[1]),(float)Math.toRadians(degrees[0]));}
}
