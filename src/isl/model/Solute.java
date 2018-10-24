/*
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

public /*strictfp*/ class Solute extends isl.io.Propertied implements bsg.util.TypeString {
  private static int instanceCount = 0;
  public int id = -1;
  public String type = null;
  public String getType() { return type; }
  boolean bindable = false;
  public boolean isBindable() { return bindable; }
  /** Creates a new instance of Solute */
  public Solute(String t, boolean b) {
     if (t != null) type = t;
     else throw new RuntimeException("Solute: type cannot be null.");
     bindable = b;
     id = instanceCount++;
  }
}
