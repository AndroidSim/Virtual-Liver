/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model.cell;

import isl.model.SSGrid;
import isl.model.Solute;
import isl.model.SoluteType;

public class KupfferCell extends Cell {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KupfferCell.class);
  static sim.util.Double2D CROSS_PROB = new sim.util.Double2D(1.0, 1.0);
  public static void setMembraneCrossProb(sim.util.Double2D mcp) { log.debug("Setting mcp = "+mcp); CROSS_PROB = mcp; }
  @Override
  public boolean canCross(Solute s, Dir d) { return canCross(s,d,CROSS_PROB); }

  public KupfferCell(SSGrid p, ec.util.MersenneTwisterFast rng, int x, int y) {
    super(p,rng);
    setLoc(x,y);
        
    actionShuffler.clear(); //Removes BindingHandler        
    actionShuffler.add((Runnable) () -> { handleInflammation(); });
    actionShuffler.add((Runnable) () -> { handleToxicMediator(); });
    actionShuffler.add((Runnable) () -> { handleProtectiveMediator(); });
    //Must add handleDegradation() again because actionShuffler was just cleared.
    actionShuffler.add((Runnable) () -> { handleDegradation(myGrid); });
  }
  
  public static SoluteType cytokineType = null;
  public static SoluteType protectiveMediatorType = null;
  public static SoluteType toxicMediatorType = null;

  public void handleInflammation() {
    //Count the number of inflammatory stimuli and Cytokines in the Cell
    int numInflammatoryStimuli = 0;
    int numCytokines = 0;
    for (Object o : solutes) {
      Solute s = (Solute) o;
      if (s.hasProperty("inflammatory") && ((Boolean) s.getProperty("inflammatory")))
        numInflammatoryStimuli++;
      if (s.type.equals("Cytokine"))
        numCytokines++;
    }

    //If past inflammatory threshold, there's a chance to produce Cytokine
    if (numInflammatoryStimuli >= myGrid.ss.hepStruct.inflammatoryStimulusThreshold) {
      double probability = 1.0 - Math.exp(-1 * (numInflammatoryStimuli - myGrid.ss.hepStruct.inflammatoryStimulusThreshold) / myGrid.ss.hepStruct.exponentialFactor);

      double draw = cellRNG.nextDouble();
      if (draw <= probability) addCytokine();
    }
  }
    
  public Solute addCytokine() {
    //Set the Cytokine BolusEntry once
    if (cytokineType == null) {
      java.util.ArrayList<SoluteType> soluteTypes = myGrid.ss.hepStruct.model.delivery.allSolute;
      Boolean foundCytokine = false;
      for (SoluteType st : soluteTypes) {
        if (st.tag.equals("Cytokine")) {
          cytokineType = st;
          foundCytokine = true;
          break;
        }
      }
      if (!foundCytokine)
        throw new RuntimeException("There must be a Cytokine Solute type in order to create Cytokine objects.");
    }
        
    //Create the Cytokine
    Solute cytokine = new Solute(cytokineType.tag, cytokineType.bindable);
    cytokine.setProperties(cytokineType.properties);

    //Add the Cytokine
    present(cytokine);
    
    return cytokine;
  }
    
  public void handleProtectiveMediator() {
    //Count the number of toxic stimuli in the cell
    int numToxicStimuli = 0;
    int numProtectiveMediators = 0;
    for (Object o : solutes) {
      Solute s = (Solute) o;
      if (s.hasProperty("inducesProtectiveMediator") && ((Boolean) s.getProperty("inducesProtectiveMediator")))
        numToxicStimuli++;
      if (s.type.equals("ProtectiveMediator"))
        numProtectiveMediators++;
    }
 
    //If past inducesProtectionMediatorThreshold, there's a chance to produce ProtectiveMediator
    if (numToxicStimuli >= myGrid.ss.hepStruct.inducesProtectiveMediatorThreshold) {
      double probability = 1.0 - Math.exp(-1 * (numToxicStimuli - myGrid.ss.hepStruct.inducesProtectiveMediatorThreshold) / myGrid.ss.hepStruct.exponentialFactor);

      double draw = cellRNG.nextDouble();
      if (draw <= probability) addProtectiveMediator();
    }
  }
    
  public Solute addProtectiveMediator() {

    //Set the ProtectiveMediator BolusEntry once
    if (protectiveMediatorType == null) {
      java.util.ArrayList<SoluteType> soluteTypes = myGrid.ss.hepStruct.model.delivery.allSolute;
      Boolean foundProtectiveMediator = false;
      for (SoluteType be : soluteTypes) {
        if (be.tag.equals("ProtectiveMediator")) {
          protectiveMediatorType = be;
          foundProtectiveMediator = true;
          break;
        }
      }
      if (!foundProtectiveMediator)
        throw new RuntimeException("There must be a Protective Mediator bolus entry.");
    }
        
    //Create the Protective Mediator
    Solute protectiveMediator = new Solute(protectiveMediatorType.tag, protectiveMediatorType.bindable);
    protectiveMediator.setProperties(protectiveMediatorType.properties);

    //Add the Protective Mediator
    present(protectiveMediator);

    return protectiveMediator;
  }
    
  public void handleToxicMediator() {
    //Count the number of inducers and inhibitors in the Cell
    int numInduceToxicMediator = 0;
    int numInhibitsToxicMediator = 0;
    int numToxicMediator = 0;
    for (Object o : solutes) {
      Solute s = (Solute) o;
      if (s.hasProperty("inducesToxicMediator") && ((Boolean) s.getProperty("inducesToxicMediator")))
        numInduceToxicMediator++;
      if (s.hasProperty("inhibitsToxicMediator") && ((Boolean) s.getProperty("inhibitsToxicMediator")))
        numInhibitsToxicMediator++;
      if (s.type.equals("ToxicMediator")) 
        numToxicMediator++;
    }
        
    //If past inflammatory threshold, there's a chance to produce Cytokine
    if ((numInduceToxicMediator - numInhibitsToxicMediator) >= myGrid.ss.hepStruct.inducesToxicMediatorThreshold) {
      double probability = 1.0 - Math.exp(-1 * ((numInduceToxicMediator - numInhibitsToxicMediator) - myGrid.ss.hepStruct.inducesToxicMediatorThreshold) / myGrid.ss.hepStruct.exponentialFactor);

      double draw = cellRNG.nextDouble();
      if (draw <= probability) addToxicMediator();
    }
  }
    
  public Solute addToxicMediator() {

    //Set the ProtectiveMediator BolusEntry once
    if (toxicMediatorType == null) {
      java.util.ArrayList<SoluteType> soluteTypes = myGrid.ss.hepStruct.model.delivery.allSolute;
      Boolean foundToxicMediator = false;
      for (SoluteType be : soluteTypes) {
        if (be.tag.equals("ToxicMediator")) {
          toxicMediatorType = be;
          foundToxicMediator = true;
          break;
        }
      }
      if (!foundToxicMediator)
        throw new RuntimeException("There must be a Toxic Mediator bolus entry.");
    }
        
    //Create the toxic Mediator
    Solute toxicMediator = new Solute(toxicMediatorType.tag, toxicMediatorType.bindable);
    toxicMediator.setProperties(toxicMediatorType.properties);

    //Add the Toxic Mediator
    present(toxicMediator);
    
    return toxicMediator;
  }
}

