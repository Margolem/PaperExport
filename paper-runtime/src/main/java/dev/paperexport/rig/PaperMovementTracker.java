package dev.paperexport.rig;

/** Uses measured horizontal travel so gravity and head turns do not start walking. */
public final class PaperMovementTracker {
    private int walkingTicks;

    public boolean sample(double deltaX,double deltaZ,int elapsedTicks){
        if(elapsedTicks<=0||!Double.isFinite(deltaX)||!Double.isFinite(deltaZ))return reset();
        double distance=Math.hypot(deltaX,deltaZ);
        if(distance>elapsedTicks*.8)return reset(); // A teleport is not a walk cycle.
        if(distance/elapsedTicks>=.025)walkingTicks=5;
        else walkingTicks=Math.max(0,walkingTicks-elapsedTicks);
        return walkingTicks>0;
    }

    public boolean reset(){walkingTicks=0;return false;}
}
