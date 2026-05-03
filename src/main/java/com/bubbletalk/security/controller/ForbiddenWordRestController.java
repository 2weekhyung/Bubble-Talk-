package com.bubbletalk.security.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.security.dto.req.ForbiddenWordAddReqDto;
import com.bubbletalk.security.dto.res.ForbiddenWordResDto;
import com.bubbletalk.security.entity.ForbiddenWord;
import com.bubbletalk.security.repository.ForbiddenWordRepository;
import com.bubbletalk.security.service.ForbiddenWordService;
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

    private final ForbiddenWordService forbiddenWordService;
    private final ForbiddenWordRepository forbiddenWordRepository;

    /**
     * [조회] 현재 등록된 모든 금칙어 목록을 가져옵니다.
     */
    @GetMapping
    public ResponseEntity<BaseResDto> getAllWords() {
        // 조회의 경우 단순 리스트 반환이므로 Repository를 직접 사용할 수도 있지만,
        // 일관성을 위해 Service에서 처리하거나 Repository 결과를 DTO로 변환합니다.
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
        forbiddenWordService.addWord(reqDto.getWord());
        return ResponseEntity.ok(BaseResDto.ok());
    }

    /**
     * [삭제] 특정 금칙어를 해제(삭제)합니다.
     * @param id 금칙어 고유 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResDto> deleteWord(@PathVariable Long id) {
        forbiddenWordService.deleteWord(id);
        return ResponseEntity.ok(BaseResDto.ok());
    }
}
