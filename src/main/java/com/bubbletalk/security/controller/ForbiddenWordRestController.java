package com.bubbletalk.security.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.security.dto.req.ForbiddenWordAddReqDto;
import com.bubbletalk.security.dto.res.ForbiddenWordResDto;
import com.bubbletalk.security.entity.ForbiddenWord;
import com.bubbletalk.security.repository.ForbiddenWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [금칙어 관리용 API 컨트롤러]
 * 관리자가 서비스의 무결성을 유지하기 위해 금칙어를 제어하는 입구입니다.
 */
@RestController
@RequestMapping("/api/admin/forbidden-words")
@RequiredArgsConstructor
public class ForbiddenWordRestController {

    private final ForbiddenWordRepository forbiddenWordRepository;

    /**
     * [조회] 현재 등록된 모든 금칙어 목록을 가져옵니다.
     */
    @GetMapping
    public ResponseEntity<BaseResDto> getAllWords() {
        List<ForbiddenWordResDto> words = forbiddenWordRepository.findAll().stream()
                .map(fw -> ForbiddenWordResDto.builder()
                        .id(fw.getId())
                        .word(fw.getWord())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(BaseResDto.ok(words));
    }

    /**
     * [추가] 새로운 금칙어를 등록합니다.
     */
    @PostMapping
    public ResponseEntity<BaseResDto> addWord(@RequestBody ForbiddenWordAddReqDto reqDto) {
        String word = reqDto.getWord();
        // 이미 등록된 단어인지 확인 후 저장
        if (forbiddenWordRepository.existsByWord(word)) {
            throw new IllegalArgumentException("이미 등록된 금칙어입니다: " + word);
        }

        ForbiddenWord forbiddenWord = ForbiddenWord.builder()
                .word(word)
                .build();
        
        ForbiddenWord saved = forbiddenWordRepository.save(forbiddenWord);
        ForbiddenWordResDto resDto = ForbiddenWordResDto.builder()
                .id(saved.getId())
                .word(saved.getWord())
                .build();
        
        return ResponseEntity.ok(BaseResDto.ok(resDto));
    }

    /**
     * [삭제] 특정 금칙어를 해제(삭제)합니다.
     * @param id 금칙어 고유 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResDto> deleteWord(@PathVariable Long id) {
        forbiddenWordRepository.deleteById(id);
        return ResponseEntity.ok(BaseResDto.ok());
    }
}
