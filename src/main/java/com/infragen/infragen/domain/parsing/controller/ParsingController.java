package com.infragen.infragen.domain.parsing.controller;

import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class ParsingController {
    private final ParsingService parsingService;

    @PostMapping("/{projectId}/parsing")
    public ResponseEntity<ParsingResultDTO> parsingInfraStructure(@PathVariable Long projectId ,@RequestBody ParsingReqDTO requestDTO){
        ParsingResultDTO result = parsingService.parsing(requestDTO , projectId);

        return ResponseEntity.ok(result);
    }
}