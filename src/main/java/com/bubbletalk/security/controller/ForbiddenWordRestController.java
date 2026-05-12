package com.bubbletalk.security.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.security.dto.req.ForbiddenWordAddReqDto;
import com.bubbletalk.security.dto.res.ForbiddenWordResDto;
import com.bubbletalk.security.entity.ForbiddenWord;
import com.bubbletalk.security.repository.ForbiddenWordRepository;
import com.bubbletalk.security.service.ForbiddenWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [금칙어 관리용 API 컨트롤러]
 * 관리자가 서비스의 무결성을 유지하기 위해 금칙어를 제어하는 입구입니다.
 */
@Tag(name = "Admin - Forbidden Words", description = "관리자용 금칙어 관리 API")
@RestController
@RequestMapping("/api/admin/forbidden-words")
@RequiredArgsConstructor
public class ForbiddenWordRestController {

    private final ForbiddenWordService forbiddenWordService;
    private final ForbiddenWordRepository forbiddenWordRepository;

    /**
     * [조회] 현재 등록된 모든 금칙어 목록을 가져옵니다.
     */
    @Operation(summary = "금칙어 목록 조회", description = "현재 DB에 저장된 모든 금칙어를 가져옵니다.")
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
    @Operation(summary = "금칙어 추가", description = "새로운 단어를 금칙어로 등록하고 Redis 캐시에 반영합니다.")
    @PostMapping
    public ResponseEntity<BaseResDto> addWord(@RequestBody ForbiddenWordAddReqDto reqDto) {
        forbiddenWordService.addWord(reqDto.getWord());
        return ResponseEntity.ok(BaseResDto.ok());
    }

    /**
     * [갱신] DB의 금칙어 목록을 Redis 캐시로 강제 동기화합니다.
     */
    @Operation(summary = "금칙어 캐시 갱신", description = "DB의 데이터를 바탕으로 Redis 금칙어 캐시를 강제로 다시 로드합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<BaseResDto> refreshCache() {
        forbiddenWordService.refreshCache();
        return ResponseEntity.ok(BaseResDto.ok("금칙어 캐시가 성공적으로 갱신되었습니다."));
    }

    /**
     * [삭제] 특정 금칙어를 해제(삭제)합니다.
     * @param id 금칙어 고유 ID
     */
    @Operation(summary = "금칙어 삭제", description = "특정 ID의 금칙어를 삭제하고 Redis 캐시에서 제거합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResDto> deleteWord(@PathVariable Long id) {
        forbiddenWordService.deleteWord(id);
        return ResponseEntity.ok(BaseResDto.ok());
    }
}
