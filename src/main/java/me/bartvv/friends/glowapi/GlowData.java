package me.bartvv.friends.glowapi;

import java.util.Map;
import java.util.UUID;

import com.google.common.collect.Maps;

public class GlowData
{
  public Map<UUID, GlowAPI.Color> colorMap = Maps.newHashMap();
  
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if ((o == null) || (getClass() != o.getClass())) {
      return false;
    }
    GlowData glowData = (GlowData)o;
    
    return glowData.colorMap == null ? true : this.colorMap != null ? this.colorMap.equals(glowData.colorMap) : false;
  }
  
  public int hashCode()
  {
    return this.colorMap != null ? this.colorMap.hashCode() : 0;
  }
}
