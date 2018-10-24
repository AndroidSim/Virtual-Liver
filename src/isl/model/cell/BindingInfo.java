/*
 * Copyright 2014-2016 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 */
package isl.model.cell;

import isl.model.EnzymeGroup;
import isl.model.Solute;
import java.util.List;

public interface BindingInfo {
    
    //Accessors
    public java.util.ArrayList<EnzymeGroup> getEnzymeGroups();
//    public java.util.ArrayList<Solute> getSolutes();
    public List<Solute> listSolute();
    public double getBindingProbability(EnzymeGroup eg);
    public int getBindmin();
    public int getBindmax();
    public boolean isBound(Solute s);
    
    //Methods to manipulate state information
    public void scheduleRelease(Solute s, EnzymeGroup eg);
}
