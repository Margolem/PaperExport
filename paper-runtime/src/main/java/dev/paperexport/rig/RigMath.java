package dev.paperexport.rig;

import dev.paperexport.format.PaperEntityDefinition.Bone;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RigMath {
    private RigMath() {}
    /** Model front is -Z; vanilla yaw zero faces +Z. */
    public static float rootYaw(float entityYaw){return (float)Math.toRadians(180-entityYaw);}
    public static Map<String,Matrix4f> worldMatrices(List<Bone> bones,Map<String,PaperAnimationController.Pose> poses,float rootYaw,float rootScale){
        Map<String,Bone> byId=new HashMap<>();for(Bone b:bones)byId.put(b.id,b);
        Map<String,Matrix4f> result=new HashMap<>();Matrix4f root=new Matrix4f().rotateY(rootYaw).scale(rootScale);
        for(Bone bone:bones)visit(bone,byId,poses,result,root);
        return result;
    }
    private static Matrix4f visit(Bone bone,Map<String,Bone> bones,Map<String,PaperAnimationController.Pose> poses,Map<String,Matrix4f> result,Matrix4f root){
        Matrix4f existing=result.get(bone.id);if(existing!=null)return existing;
        Bone parent=bone.parent==null?null:bones.get(bone.parent);
        Matrix4f m=new Matrix4f(parent==null?root:visit(parent,bones,poses,result,root));
        PaperAnimationController.Pose pose=poses.getOrDefault(bone.id,new PaperAnimationController.Pose(new Vector3f(),new Quaternionf(),new Vector3f(1,1,1)));
        m.translate((float)((bone.pivot[0]-(parent==null?0:parent.pivot[0])+pose.position().x)/16),
                    (float)((bone.pivot[1]-(parent==null?0:parent.pivot[1])+pose.position().y)/16),
                    (float)((bone.pivot[2]-(parent==null?0:parent.pivot[2])+pose.position().z)/16));
        m.rotate(PaperAnimationController.quat(bone.rotation)).rotate(pose.rotation());
        m.scale((float)bone.scale[0]*pose.scale().x,(float)bone.scale[1]*pose.scale().y,(float)bone.scale[2]*pose.scale().z);
        result.put(bone.id,m);return m;
    }
}
