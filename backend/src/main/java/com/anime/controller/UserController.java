package com.anime.controller;

import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.UserPrincipal;
import com.anime.dto.request.FolderReorderRequest;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.FavoriteFolder;
import com.anime.entity.User;
import com.anime.repository.FavoriteFolderRepository;
import com.anime.repository.UserRepository;
import com.anime.service.UserService;
import com.anime.util.SensitiveWordFilter;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import java.util.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DtoMapper dtoMapper;
    private final FavoriteFolderRepository favoriteFolderRepository;
    private final SensitiveWordFilter sensitiveWordFilter;

    public UserController(UserService userService,
                          UserRepository userRepository,
                          DtoMapper dtoMapper,
                          FavoriteFolderRepository favoriteFolderRepository,
                          SensitiveWordFilter sensitiveWordFilter) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.dtoMapper = dtoMapper;
        this.favoriteFolderRepository = favoriteFolderRepository;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @GetMapping("/favorites/folders")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFavoriteFolders(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Map<String, Object>> folders = userService.getFavoriteFolders(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(folders));
    }

    @PostMapping("/favorites/folders")
    public ResponseEntity<ApiResponse<FavoriteFolder>> createFavoriteFolder(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name is required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        if (favoriteFolderRepository.existsByUserIdAndName(userPrincipal.getId(), name.trim())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name already exists"));
        }
        List<FavoriteFolder> existingFolders = favoriteFolderRepository.findByUserIdOrderBySortOrderAsc(userPrincipal.getId());
        for (FavoriteFolder f : existingFolders) {
            f.setSortOrder(f.getSortOrder() + 1);
            favoriteFolderRepository.save(f);
        }
        FavoriteFolder folder = new FavoriteFolder();
        folder.setUserId(userPrincipal.getId());
        folder.setName(name.trim());
        folder.setSortOrder(0);
        FavoriteFolder savedFolder = favoriteFolderRepository.save(folder);
        return ResponseEntity.ok(ApiResponse.success("Folder created", savedFolder));
    }

    @PutMapping("/favorites/folders/{folderId}")
    public ResponseEntity<ApiResponse<FavoriteFolder>> updateFavoriteFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name is required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        String trimmedName = name.trim();
        if (favoriteFolderRepository.existsByUserIdAndName(userPrincipal.getId(), trimmedName)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name already exists"));
        }
        FavoriteFolder folder = folderOpt.get();
        folder.setName(trimmedName);
        FavoriteFolder updatedFolder = favoriteFolderRepository.save(folder);
        return ResponseEntity.ok(ApiResponse.success("Folder renamed", updatedFolder));
    }

    @DeleteMapping("/favorites/folders/{folderId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteFolder(
            @PathVariable Long folderId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        userService.deleteFolderAndFavorites(userPrincipal.getId(), folderId);
        favoriteFolderRepository.deleteById(folderId);
        return ResponseEntity.ok(ApiResponse.success("Folder deleted", null));
    }

    @PutMapping("/favorites/folders/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderFavoriteFolders(
            @RequestBody FolderReorderRequest request,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Long> folderOrder = request.getFolderOrder();
        if (folderOrder == null || folderOrder.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder order is required"));
        }
        for (int i = 0; i < folderOrder.size(); i++) {
            Long folderId = folderOrder.get(i);
            Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
            if (folderOpt.isPresent() && folderOpt.get().getUserId().equals(userPrincipal.getId())) {
                folderOpt.get().setSortOrder(i);
                favoriteFolderRepository.save(folderOpt.get());
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Folders reordered", null));
    }

    @GetMapping("/favorites/folders/{folderId}")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getFavoritesByFolder(
            @PathVariable Long folderId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(dtoMapper.toAnimeDtoList(userService.getFavoritesByFolder(userPrincipal.getId(), folderId))));
    }

    @PostMapping("/favorites/folders/{folderId}/anime/{animeId}")
    public ResponseEntity<ApiResponse<Void>> addFavoriteToFolder(
            @PathVariable Long folderId,
            @PathVariable Long animeId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        userService.addFavoriteToFolder(userPrincipal.getId(), animeId, folderId);
        return ResponseEntity.ok(ApiResponse.success("Added to folder", null));
    }

    @DeleteMapping("/favorites/folders/{folderId}/anime/{animeId}")
    public ResponseEntity<ApiResponse<Void>> removeFavoriteFromFolder(
            @PathVariable Long folderId,
            @PathVariable Long animeId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        userService.removeFavoriteFromFolder(userPrincipal.getId(), animeId, folderId);
        return ResponseEntity.ok(ApiResponse.success("Removed from folder", null));
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getFavorites(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(dtoMapper.toAnimeDtoList(userService.getFavorites(userPrincipal.getId()))));
    }

    @DeleteMapping("/favorites/{animeId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable Long animeId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        userService.removeFavorite(userPrincipal.getId(), animeId);
        return ResponseEntity.ok(ApiResponse.success("Removed from favorites", null));
    }

    @GetMapping("/watch-history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWatchHistory(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(userService.getWatchHistory(userPrincipal.getId())));
    }

    @PostMapping("/watch-history/{animeId}")
    public ResponseEntity<ApiResponse<Void>> updateWatchProgress(
            @PathVariable Long animeId,
            @RequestParam int progress,
            @RequestParam(defaultValue = "false") boolean completed,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        userService.updateWatchProgress(userPrincipal.getId(), animeId, progress, completed);
        return ResponseEntity.ok(ApiResponse.success("Progress updated", null));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getUserRecommendations(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(dtoMapper.toAnimeDtoList(userService.getRecommendations(userPrincipal.getId(), limit))));
    }

    @PutMapping("/username")
    public ResponseEntity<ApiResponse<Void>> updateUsername(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        String username = body.get("username");
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username is required"));
        }

        String trimmedUsername = username.trim();
        if (trimmedUsername.length() < 3 || trimmedUsername.length() > 30) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username must be 3-30 characters"));
        }

        if (!sensitiveWordFilter.validate(trimmedUsername)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("存在违规字符"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("User not found"));
        }

        if (userRepository.existsByUsername(trimmedUsername)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("该用户名已被注册"));
        }

        user.setUsername(trimmedUsername);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("Username updated", null));
    }

    @PutMapping("/signature")
    public ResponseEntity<ApiResponse<Void>> updateSignature(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        String signature = body.get("signature");
        if (signature != null && signature.length() > 100) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Signature must be within 100 characters"));
        }

        String filteredSignature = sensitiveWordFilter.filter(signature);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        userRepository.findById(userPrincipal.getId()).ifPresent(user -> {
            user.setSignature(filteredSignature);
            userRepository.save(user);
        });

        return ResponseEntity.ok(ApiResponse.success("Signature updated", null));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> updateAvatar(
            @RequestParam("avatar") MultipartFile file,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("User not found"));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String newFilename = UUID.randomUUID().toString() + extension;
        
        try {
            Path uploadDir = Paths.get("uploads/avatars");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            Path targetLocation = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            String avatarUrl = "/uploads/avatars/" + newFilename;
            
            String oldAvatarUrl = user.getAvatarUrl();
            if (oldAvatarUrl != null && oldAvatarUrl.startsWith("/uploads/avatars/")) {
                String oldFilename = oldAvatarUrl.substring("/uploads/avatars/".length());
                Path oldFilePath = uploadDir.resolve(oldFilename);
                if (Files.exists(oldFilePath)) {
                    Files.delete(oldFilePath);
                }
            }
            
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
            
            return ResponseEntity.ok(ApiResponse.success("Avatar updated", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to upload avatar"));
        }
    }

}