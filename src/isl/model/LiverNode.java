/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import bsg.util.CollectionUtils;
import bsg.util.MutableInt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public strictfp abstract class LiverNode extends Compartment implements LiverNodes {
  private static final long serialVersionUID = 7917013348869340551L;
   public int id = -1;
   public HepStruct hepStruct = null;
   public int gshUpEliminated = 0;
   ArrayList<LiverNode> outNodes = null; // convenient holders for nodes
   ArrayList<LiverNode> inNodes = null;
   LinkedHashMap<LiverNode, Double> faninWeights = null;
   LinkedHashMap<LiverNode, Double> fanoutWeights = null;
   public int priorPathLength = -Integer.MAX_VALUE; // length in grid points from PV to my inlet
   public int postPathLength = -Integer.MAX_VALUE; // length in grid points from outlet to CV
   
   static org.slf4j.Logger log = null;
   public void setLogger(org.slf4j.Logger logger) {
     log = logger;
  }
   
   /** Creates a new instance of LiverNode */
   public LiverNode(ec.util.MersenneTwisterFast r, HepStruct l) {
     super(r);
     if ( l!=null ) hepStruct = l;
     else throw new RuntimeException("LiverNode: Parent hepStruct can't be null.");
   }
   
   public void setID(int i) {
      if ( i>=0 ) id = i;
      else throw new RuntimeException("LiverNode.setID(" + i + "): ID can't be negative.");
   }
   public int getID() { return id; }
   
  @Override
   public void step(sim.engine.SimState state) {
      stepPhysics();
      stepBioChem();
   }
   
  @Override
   public abstract void stepPhysics();

   /**
    * attempts to distribute the passed solute to the output nodes
    * those that it can move go into the return structure and are
    * removed from the array passed in
    * @param s
    * @return the solute we are moving
    */
   protected ArrayList<Solute> distribute(Map<String,Number> sm, CompartmentType c) {
     long solute_map_size = CollectionUtils.sum_mi(sm);
     ArrayList<Solute> totalMoved = null;
     if (sm != null && solute_map_size > 0) {
       totalMoved = new ArrayList<>();
       // handle fan[in|out]Weights
       sim.util.Bag tmpNodes = null;
       java.util.HashMap<LiverNode, Double> weights = null;
       if (c == SS.BILE) {
         if (faninWeights == null)
           faninWeights = computeDistWeights(SSGrid.Dir.N);
         weights = faninWeights;
         tmpNodes = new sim.util.Bag(inNodes);
       } else {
         if (fanoutWeights == null)
           fanoutWeights = computeDistWeights(SSGrid.Dir.S);
         weights = fanoutWeights;
         tmpNodes = new sim.util.Bag(outNodes);
       }
       tmpNodes.shuffle(hepStruct.hepStructRNG);
       
       for (Object o : tmpNodes) {
         LiverNode n = (LiverNode) o;
         ArrayList<Solute> moved = null;
         double ratio = weights.get(n);
         solute_map_size = CollectionUtils.sum_mi(sm); // recomputed as pool Δs
         long numToPush = (long) StrictMath.ceil(ratio*solute_map_size);
         // min = 1 since distWeights might be too small and leave solute forever
         // if numToPush is too small, just try to push them all
         if (numToPush <= 0) numToPush = solute_map_size;
         moved = push(numToPush, sm, n, c);
         //if (id == 0)
         //  log.debug("LN:"+id+ " moved " + moved.size() + "/" + numToPush + " to compartment " + c + " of " + n.id);
         totalMoved.addAll(moved);
       }
     }

     return totalMoved;
   }
   
   protected ArrayList<Solute> push(long number, Map<String,Number> sm, LiverNode n, CompartmentType c) {
     long solute_map_size = CollectionUtils.sum_mi(sm);
      ArrayList<Solute> placed = new ArrayList<>();

      // push each type according to their ratios of the total      
      for (Map.Entry<String,Number> me : sm.entrySet()) {
        SoluteType de = hepStruct.model.delivery.allSolute.stream().filter((o) -> (o.tag.equals(me.getKey()))).findAny().get();
        long num_this_type = (long) StrictMath.ceil(number*me.getValue().doubleValue()/solute_map_size);
        for (int count=0 ; count<num_this_type ; count++) {
          Solute solute = new Solute(me.getKey(),de.bindable);
          solute.setProperties(de.properties);
          if (n.accept(solute, c)) {
            placed.add(solute);
            ((MutableInt)sm.get(solute.type)).sub(1); // remove one from the pool
          } else break; // because that tgt node is full
        }
      }
      
      
      
      return placed;
   }

  @Override
   public abstract void stepBioChem();

  /**
   * @return Calculate/Return the amount of solute that can enter as if empty
   */
  @Override
   public abstract double getInletCap();
   public abstract boolean accept(Solute s, CompartmentType c);

   protected LinkedHashMap<LiverNode, Double> computeDistWeights(SSGrid.Dir dir) {
      double totalInletCap = 0.0F;
      // first get the total CC and initialize [in|out]Nodes
      ArrayList<LiverNode> tgtNodes = (dir == SSGrid.Dir.S ? outNodes : inNodes);
      if ( tgtNodes==null ) {
         sim.util.Bag edges = (dir == SSGrid.Dir.S ? hepStruct.getEdgesOut(this) : hepStruct.getEdgesIn(this));
         tgtNodes = new ArrayList<>(edges.numObjs);
         for (Object e : edges) {
            LiverNode ln = (LiverNode) (dir == SSGrid.Dir.S
                    ? ((LiverEdge)e).to()
                    : ((LiverEdge)e).from());
            tgtNodes.add(ln);
            // below works because all getInletCap() does is calculate circumference
            // if getInletCap() changes, the flow direction may become important
            totalInletCap += ln.getInletCap();
         }
         if (dir == SSGrid.Dir.S) outNodes = tgtNodes;
         else inNodes = tgtNodes;
      } else {
         for (LiverNode ln : tgtNodes) totalInletCap += ln.getInletCap();
      }
      LinkedHashMap<LiverNode, Double> retVal = new LinkedHashMap<>(tgtNodes.size());
      for (Object n : tgtNodes) {
         LiverNode ln = (LiverNode) n;
         retVal.put(ln, ln.getInletCap()/totalInletCap);
      }
      return retVal;
   }
   /**
    * @param tgt
    * @return
    */
   public boolean linksTo(LiverNode tgt) {
     boolean retVal = false;
     for (Object e : hepStruct.getEdgesOut(this)) {
       LiverEdge le = (LiverEdge)e;
       if (tgt.equals(le.to())) {
               retVal = true;
               break;
       }
     }
     return retVal;
   }
}
