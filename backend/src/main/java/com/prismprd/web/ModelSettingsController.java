package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.common.AiRequestGuard;
import com.prismprd.model.ModelClientRegistry;
import com.prismprd.model.ModelSettingsService;
import com.prismprd.security.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/model-settings")
@RequiredArgsConstructor
public class ModelSettingsController {
    private final ModelSettingsService settings;
    private final ModelClientRegistry registry;
    private final CurrentUserService users;
    private final AiRequestGuard guard;
    public record Activation(@NotNull UUID id) {}
    public record TestRequest(UUID id, @Valid @NotNull ModelSettingsService.Input configuration) {}

    @GetMapping public ApiResponse<ModelSettingsService.Settings> list() { return ApiResponse.ok(settings.list(users.currentUserId())); }
    @PostMapping public ApiResponse<ModelSettingsService.Summary> create(@Valid @RequestBody ModelSettingsService.Input input) {
        return ApiResponse.ok(settings.save(users.currentUserId(),null,input));
    }
    @PutMapping("/{id}") public ApiResponse<ModelSettingsService.Summary> update(@PathVariable UUID id,
            @Valid @RequestBody ModelSettingsService.Input input) {
        return ApiResponse.ok(settings.save(users.currentUserId(),id,input));
    }
    @PutMapping("/active") public ApiResponse<Void> activate(@Valid @RequestBody Activation input) {
        settings.activate(users.currentUserId(),input.id()); return ApiResponse.ok();
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable UUID id,
            @RequestParam long revision) {
        settings.delete(users.currentUserId(),id,revision); return ApiResponse.ok();
    }
    @PostMapping("/test") public ApiResponse<ModelClientRegistry.TestResult> test(@Valid @RequestBody TestRequest input) {
        UUID user=users.currentUserId();
        try (var lease=guard.acquire("model-test",user)) {
            return ApiResponse.ok(registry.test(user,input.id(),input.configuration()));
        }
    }
}

