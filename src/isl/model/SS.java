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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import isl.model.cell.KupfferCell;
import isl.model.cell.Hepatocyte;
import isl.model.cell.EC;
import isl.model.cell.Cell;

public class SS extends LiverNode implements LiverNodes {
  
  private static final long serialVersionUID = 2911232015318652318L;
  public static final CompartmentType BILE = new CompartmentType("QUEUE");
  public ArrayList<Solute> solutes = new ArrayList<>();
  public ArrayList<Solute> getSolutes() { return solutes; }
  @Override
  public Map<String,Number> getSoluteMap() { return CollectionUtils.countObjectsByType(solutes); }
  public BileCanal bileCanal = null;
  public BileCanal getBileCanal() { return bileCanal; }
  InnerSpace innerSpace = null;
  public InnerSpace getInnerSpace() { return innerSpace; }
  public HepSpace hSpace = null;
  public HepSpace getHSpace() {return hSpace;}
  SSGrid eSpace = null;
  public SSGrid getESpace() {return eSpace;}
  public SSGrid sod = null;
  public SSGrid getSoD() {return sod;}

  public sim.util.Bag cells = new sim.util.Bag();
  
  /**
   * collect the cells that have necrosed to make measurement more convenient
   */
  public Map<Hepatocyte,Double> necroticHepatocytes = new HashMap<>();
  public void necrotic(Hepatocyte h) {
    necroticHepatocytes.put(h,hepStruct.model.getTime());
  }
  
  /**
   * collect the cells that have been triggered for necrosis
   */
  public HashMap<Hepatocyte,Double> necTrigCells = new HashMap<>();
  public void necTrig(Hepatocyte h) { necTrigCells.put(h,hepStruct.model.getTime()); }
  
  public int layer = -1;
  public int getLayer() {return layer;}
  public int circ = 5, length = 10;
  public int getCirc() {return circ;} public int getLength() {return length;}
  
  public int ei_thresh = -Integer.MAX_VALUE;
  public double ei_rate = Double.NaN;
  public double ei_response_factor = Double.NaN;
  public int el_thresh = -Integer.MAX_VALUE;
  public double el_rate = Double.NaN;
  public double el_response_factor = Double.NaN;

  public SS ( ec.util.MersenneTwisterFast r, HepStruct l, ec.util.ParameterDatabase pd ) {
    super(r, l);
    SSParams.loadParams(this,pd);
  }
  public void setGeometry(int c, int l) {
    if (c < 0) throw new RuntimeException("SS circumference must be > 0");
    circ = c;
    if (l < 0) throw new RuntimeException("SS length must be > 0");
    length = l;
    innerSpace = new InnerSpace(this, circ, length, SSParams.flowRate);
    innerSpace.setLogger(log);
    bileCanal = new BileCanal(this, circ, length);

    // construct the spaces in concentric order
    ArrayList<SSGrid> space_array = new ArrayList<>();
    space_array.add(innerSpace);
      
    if (SSParams.ecdens > 0.0) {
      eSpace = new SSGrid(this, circ, length, null, null);
      //log.debug("SS.setGeometry() - eSpace = "+eSpace);
      eSpace.setLogger(log);
      space_array.add(eSpace);
    }
    if (SSParams.useSoD) {
      sod = new SSGrid(this, circ, length, null, null);
      sod.setLogger(log);
      space_array.add(sod);
    }
    // create the hepSpace without the sod first
    hSpace = new HepSpace(this, circ, length, null);
    hSpace.setLogger(log);
    space_array.add(hSpace);

    // now hook them up in the order they were constructed
    for (int gNdx=1 ; gNdx<space_array.size() ; gNdx++) {
      space_array.get(gNdx-1).setOutwardGrid(space_array.get(gNdx));
      space_array.get(gNdx).setInwardGrid(space_array.get(gNdx-1));
      if (gNdx < space_array.size()-1) space_array.get(gNdx).setOutwardGrid(space_array.get(gNdx+1));
    }

    // can't setProbV until they're wired together
    innerSpace.setProbV(SSParams.forward_bias, SSParams.lateral_bias);
    if (eSpace != null) eSpace.setProbV(SSParams.forward_bias, SSParams.lateral_bias);
    if (sod != null) sod.setProbV(SSParams.forward_bias, SSParams.lateral_bias);
    hSpace.setProbV(SSParams.forward_bias, SSParams.lateral_bias);
  }
  
  public void fillSpaces() {
    assert(priorPathLength != -Integer.MAX_VALUE
            && postPathLength != -Integer.MAX_VALUE);
    
    int cellCount = 0;
    if (eSpace != null) {
      eSpace.celGrid = new sim.field.grid.ObjectGrid2D(circ, length);
      int numECells = Math.round(circ*length*SSParams.ecdens);
      int numKCells = Math.round(circ*length*SSParams.kcdens);
      int totalEKCells = Math.round(circ*length*(SSParams.ecdens + SSParams.kcdens));
      //Due to potential rounding errors when ecdens + kcdens = 1.0, numKCells + numECells may be > totalEKCells,
      ////which may exceed the number of available grid spaces.
      ////To prevent the following do-while loop from never ending, use totalEKCells.
      do {
        int x = compRNG.nextInt(circ);
        int y = compRNG.nextInt(length);
        if (eSpace.celGrid.get(x, y) == null) {
          if(cellCount < numECells) {
              EC cell = new EC(eSpace, compRNG);
              cell.setLoc(x, y);
              cells.add(cell);
              eSpace.celGrid.set(x,y,cell);
          } else {
              KupfferCell cell = new KupfferCell(eSpace, compRNG, x, y);
              cells.add(cell);
              eSpace.celGrid.set(x,y,cell);
          }
          cellCount++;
        }
      } while (cellCount < totalEKCells );
    } // end if (eSpace != null)
    
    hSpace.celGrid = new sim.field.grid.ObjectGrid2D(circ, length);
    int numHCells = Math.round(circ*length*SSParams.hepdens);
    cellCount = 0;
    do {
      int x = compRNG.nextInt(circ);
      int y = compRNG.nextInt(length);
      if (hSpace.celGrid.get(x,y) == null) {
        Hepatocyte h = new Hepatocyte(hSpace, compRNG, x, y);
        //h.setLoc(x, y);
        cells.add(h);
        hSpace.celGrid.set(x,y, h);
        cellCount++;
      }
    } while (cellCount < numHCells);
  }

  @Override
  public void stepPhysics () {
    innerSpace.flow();
    bileCanal.flow(SSParams.flowRate);
    if (eSpace != null) eSpace.flow();
    if (sod != null) sod.flow();
    hSpace.flow();
  }

  @Override
  public void stepBioChem () {
    cells.shuffle(compRNG);
    for (int cNdx=0 ; cNdx<cells.numObjs ; cNdx++) {
      Cell c = (Cell) cells.objs[cNdx];
      c.iterate();
    }
  }

  /**
   * Polymorphic with LiverNode.distribute().  Like LiverNode's, the Solute are
   * moved after this call.
   * @param s list of solute to transfer
   * @param c the type of source compartment
   * @return 
   */
   protected ArrayList<Solute> distribute(ArrayList<Solute> s, CompartmentType c) {
     
     ArrayList<Solute> totalMoved = null;
     if (s != null && s.size() > 0) {
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
         long numToPush = StrictMath.round(ratio * s.size());
         // min = 1 since distWeights might be too small and leave solute forever
         // if numToPush is too small, just try to push them all
         if (numToPush <= 0) numToPush = s.size();
         moved = push(numToPush, s, n, c);
         //if (id == 0)
         //  log.debug("LN:"+id+ " moved " + moved.size() + "/" + numToPush + " to compartment " + c + " of " + n.id);
         totalMoved.addAll(moved);
       }
     }

     return totalMoved;
   }
   
   /**
    * Polymorphic with LiverNode.push().  This method modifies the list and
    * removes the Solute from the SS.solutes.
    * @param number
    * @param soluteList
    * @param n
    * @param c
    * @return 
    */
   protected ArrayList<Solute> push(long number, ArrayList<Solute> soluteList, LiverNode n, CompartmentType c) {
      ArrayList<Solute> placed = new ArrayList<>();
      for ( int sNdx=0 ; (sNdx < soluteList.size()) && (placed.size() < number) ; sNdx++ ) {
         Solute solute = soluteList.get(sNdx);
         int tries = 0;
         if (n.accept(solute, c)) placed.add(solute);
      }
      for (Solute s : placed) {
        soluteList.remove(s);
        solutes.remove(s);
      }
      return placed;
   }


   
   
  /**
   * just in case we want to cache the CC
   * @return 
   */
  @Override
  public double getInletCap () { return calcInletCap(); }
  /**
   * calculates the inlet capacity, currently the area of the inlet
   * @return 
   */
  public double calcInletCap () {
    // use the area of the inlet circle
    return bsg.util.MathUtils.area(circ) * hepStruct.model.scale;
  }

  /**
   * calculates the effective volume of the entire SS
   * @return 
   */
  public double getWholeCap () { return getInletCap()*length; }
  
  boolean tryCore(Solute s) {
    boolean retVal = false;
    if (getInletCap() - innerSpace.core.get(0).size() >= 1.0) {
      innerSpace.core.get(0).add(s);
      retVal = true;
    }
    return retVal;
  }
  
  boolean tryRim(Solute s) {
    boolean retVal = false;
    for (int xNdx = 0; xNdx < circ; xNdx++) {
      if (innerSpace.numObjectsAtLocation(xNdx, 0) < hepStruct.model.scale) {
        innerSpace.setObjectLocation(s, xNdx, 0);
        retVal = true;
        break;
      }
    }
    return retVal;
  }
  
  @Override
  public boolean accept(Solute s, CompartmentType c) {
    boolean retVal = false;
    if (c == CompartmentType.GRID) {

      // find an empty place in the 1st grid or core, depending on the
      // solute particle's core2Rim ratio.
      double draw = compRNG.nextDouble();
      if (draw < ((Double)s.properties.get("core2Rim"))) {
        retVal = tryCore(s);
        if (!retVal) retVal = tryRim(s);
      } else {
        retVal = tryRim(s);
        if (!retVal) retVal = tryCore(s);
      }

    } else if (c == SS.BILE) {
      int last = bileCanal.tube.length-1;
      if (bileCanal.getCC() - bileCanal.tube[last].size() > 1) {
        bileCanal.tube[last].add(s);
        retVal = true;
      }
    }
    // if it was accepted, add it to my solutes list
    if (retVal) {
      if (solutes == null) {
        solutes = new ArrayList<Solute>();
      }
      solutes.add(s);
    }
    return retVal;
  }
  
  @Override
  public String describe () {
    //int coreNum = 0;
    //for (int cNdx=0 ; cNdx<length ; cNdx++) coreNum += innerSpace.core.get(cNdx).size();
    ArrayList<Solute> coreSolute = innerSpace.core.getSolutes();

    // count the bound solute in the ECs and Hepatocytes
    int totalECSoluteCount = 0; int totalHSoluteCount = 0;
    int boundECSoluteCount = 0; int boundHSoluteCount = 0;
    ArrayList<Solute> ecSolute = new ArrayList<>();
    for (Object o : cells) {
      if (o instanceof EC) {
        java.util.List<Solute> sl = ((EC)o).listSolute();
        ecSolute.addAll(sl);
        totalECSoluteCount += ((EC)o).listSolute().size();
        boundECSoluteCount += ((EC)o).getAllBoundSolutes().size();
      }
      if (o instanceof Hepatocyte) {
        totalHSoluteCount += ((Hepatocyte)o).listSolute().size();
        boundHSoluteCount += ((Hepatocyte)o).getAllBoundSolutes().size();
      }
    }
    int totalBileSoluteCount = 0;
    for (ArrayList<Solute> tube : bileCanal.tube) totalBileSoluteCount += tube.size();

    StringBuilder sb = new StringBuilder();
    sb.append( "SS:" ).append( id ).append( " (layer=" ).append(layer ).append( ", circ=" )
        .append( circ ).append( ", length=" ).append( length ).append( ", solutes=" )
        .append( (solutes == null ? "[EMPTY]" : CollectionUtils.describe(CollectionUtils.countObjectsByType(solutes))) )
            //.append(", core=").append(coreNum)
            .append(", core=").append((coreSolute != null ? CollectionUtils.describe(CollectionUtils.countObjectsByType(coreSolute)) : "<null>"))
            .append(", rim=").append(CollectionUtils.describe(CollectionUtils.countObjectsByType(new ArrayList<Solute>(innerSpace.allObjects))))
            .append(", es=").append((eSpace != null ? CollectionUtils.describe(CollectionUtils.countObjectsByType(new ArrayList<Solute>(eSpace.allObjects))) : "<null>"))
            //.append(", es.intra=").append("{b=").append(boundECSoluteCount).append(", t=").append(totalECSoluteCount).append("}")
            .append(", es.intra=").append(CollectionUtils.describe(CollectionUtils.countObjectsByType(ecSolute)))
            .append(", ds=").append((sod != null ? CollectionUtils.describe(CollectionUtils.countObjectsByType(new ArrayList<Solute>(sod.allObjects))) : "<null>"))
            .append(", hs=").append(CollectionUtils.describe(CollectionUtils.countObjectsByType(new ArrayList<Solute>(hSpace.allObjects))))
            .append(", hs.intra=").append(totalHSoluteCount)
            .append(", last(hs).solute=").append(hSpace.getSoluteAtY(hSpace.getHeight()-1).size())
            .append(", last(hs).cells=").append(hSpace.getCellsAtY(hSpace.getHeight()-1).size())
            .append(", bile=").append(totalBileSoluteCount)
            .append(", gshUpEliminated=").append(gshUpEliminated)
            .append("\n" )
         ;
    // below is helpful for determining where Solute is stuck
    //sb.append("\tTotal Solutes = ").append(CollectionUtils.describe(CollectionUtils.countTypes(solutes))).append("\n");
    return sb.toString();
  }
}
