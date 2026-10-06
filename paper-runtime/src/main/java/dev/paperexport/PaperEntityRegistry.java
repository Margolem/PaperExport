package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PaperEntityRegistry {
    private Map<String,PaperEntityDefinition> definitions=Map.of();
    public PaperEntityDefinition get(String id){return definitions.get(id);}
    public Collection<PaperEntityDefinition> all(){return definitions.values();}
    public void replace(Map<String,PaperEntityDefinition> next){definitions=Map.copyOf(new LinkedHashMap<>(next));}
}
