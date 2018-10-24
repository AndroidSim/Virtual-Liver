/*
 * Copyright 2013-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * ReactionInfo.java
 * 
 */
package isl.model.cell;

import isl.model.EnzymeGroup;
import isl.model.Solute;
import isl.model.SoluteType;
import java.util.Map;

public interface ReactionInfo {
    
    //Accessors
    public Map<EnzymeGroup, Double> getRxnProbMap();
    public Map<EnzymeGroup, Map<String,Double>> getProductionMap();
    public java.util.ArrayList<SoluteType> getSoluteTypes();

    //Methods to manipulate state information
    public void present(Solute s, boolean bileAndAmp);
    public void forget(Solute s);
    public void incRxnProd(String st);
    public void incRepairCount();
}
