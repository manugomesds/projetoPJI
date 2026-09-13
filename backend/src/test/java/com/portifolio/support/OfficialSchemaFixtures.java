package com.portifolio.support;

import com.portifolio.model.*;
import com.portifolio.model.enums.*;
import java.util.*;

/** Dados explícitos de testes; nenhum default de negócio é imposto às entities. */
public final class OfficialSchemaFixtures {
    private OfficialSchemaFixtures() {}
    public static AreaArtistica area() { return area((short) 1); }
    public static AreaArtistica area(short id) {
        AreaArtistica area = new AreaArtistica(); area.setId(id);
        area.setNome(id == 1 ? "Música" : "Artes Visuais"); return area;
    }
    public static void funcoes(PerfilArtista perfil, Set<Funcao> funcoes) {
        if (perfil.getAreas().isEmpty()) {
            PerfilArtistaArea vinculo = new PerfilArtistaArea();
            vinculo.setPerfil(perfil); vinculo.setArea(area()); vinculo.setPrincipal(true);
            vinculo.setId(new PerfilArtistaAreaId(perfil.getUsuarioId(), (short) 1));
            perfil.getAreas().add(vinculo);
        }
        perfil.getAreas().iterator().next().setFuncoes(new HashSet<>(funcoes));
    }
}
