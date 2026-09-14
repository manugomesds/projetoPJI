package com.portifolio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping({"/vagas", "/vagas/", "/vagas/{*path}"})
    public String encaminharRotasReactDeVagas() {
        return "forward:/index.html";
    }

    @GetMapping({"/minhas-vagas", "/talentos", "/talentos/"})
    public String encaminharMinhasVagasReact() {
        return "forward:/index.html";
    }

    @GetMapping({"/perfis", "/perfis/", "/perfis/{*path}"})
    public String encaminharRotasReactDePerfis() {
        return "forward:/index.html";
    }

    @GetMapping({"/recuperar-senha", "/redefinir-senha"})
    public String encaminharRotasReactDoRf09() {
        return "forward:/index.html";
    }

    @GetMapping({"/login", "/cadastro"})
    public String encaminharRotasReactDeAutenticacao() {
        return "forward:/index.html";
    }

    @GetMapping({"/dashboard", "/perfil"})
    public String encaminharRotasReactDaConta() {
        return "forward:/index.html";
    }

    @GetMapping("/mensagens")
    public String encaminharMensagensReact() {
        return "forward:/index.html";
    }
}
