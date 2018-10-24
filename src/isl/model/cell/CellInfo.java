/*
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package isl.model.cell;

import isl.model.Solute;

public interface CellInfo {
    public int getDPV(boolean actual);
    public int getDCV(boolean actual);
    public double getResources();
//    public java.util.ArrayList<Solute> getSolutes();
    public java.util.List<Solute> listSolute();
    
}
