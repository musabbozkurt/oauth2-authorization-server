package mb.oauth2authorizationserver.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.model.enums.AuthorityType;
import mb.oauth2authorizationserver.model.enums.GrantType;
import mb.oauth2authorizationserver.model.request.ClientFormData;
import mb.oauth2authorizationserver.model.request.ClientUpdateFormData;
import mb.oauth2authorizationserver.model.request.UserFormData;
import mb.oauth2authorizationserver.service.AdminService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/admin")
    public String index() {
        return "index";
    }

    // ── Tokens ─────────────────────────────────────────────

    @GetMapping("/admin/tokens")
    public String listTokens(Model model,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        model.addAttribute("tokens", adminService.findAllTokens(PageRequest.of(page, size)));
        return "tokens";
    }

    @PostMapping(value = "/admin/tokens/{tokenId}/revoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeToken(@PathVariable String tokenId) {
        adminService.revokeToken(tokenId);
        return ResponseEntity.ok("Token revoked successfully");
    }

    @PostMapping(value = "/admin/tokens/expired/revoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeAllExpiredTokens() {
        long revokedCount = adminService.revokeAllExpiredTokens();
        return ResponseEntity.ok("Revoked " + revokedCount + " expired tokens");
    }

    @PostMapping(value = "/admin/tokens/all/revoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeAllTokens() {
        long revokedCount = adminService.revokeAllTokens();
        return ResponseEntity.ok("Revoked " + revokedCount + " tokens");
    }

    // ── Clients ────────────────────────────────────────────

    @GetMapping("/admin/clients")
    public String listClients(Model model,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String searchClientId) {
        Pageable pageable = PageRequest.of(page, size);
        String sanitizedClientId = sanitize(searchClientId);

        if (sanitizedClientId != null) {
            model.addAttribute(ServiceConstants.CLIENTS, adminService.searchClients(sanitizedClientId, pageable));
        } else {
            model.addAttribute(ServiceConstants.CLIENTS, adminService.findAllClients(pageable));
        }

        model.addAttribute("searchClientId", sanitizedClientId);
        return ServiceConstants.CLIENTS;
    }

    @GetMapping("/admin/clients/add")
    public String addClientForm(Model model) {
        buildClientFormModel(model, adminService.buildClientFormModel(ClientFormData.withDefaults()));
        model.addAttribute("secret", adminService.generateSecret());
        return "add-client";
    }

    @PostMapping("/admin/clients/add")
    public String addClient(Model model,
                            RedirectAttributes redirectAttrs,
                            @ModelAttribute(ServiceConstants.CLIENT) ClientFormData clientForm) {
        try {
            String error = adminService.saveClient(clientForm);
            if (StringUtils.isNotBlank(error)) {
                return addClientFormWithError(model, clientForm, error);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation while saving client: {}", e.getMessage());
            return addClientFormWithError(model, clientForm, ErrorMessageConstants.DUPLICATE_RECORD);
        }
        redirectAttrs.addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.CLIENT_SAVED);
        return "redirect:/admin/clients";
    }

    @GetMapping("/admin/clients/{clientId}/edit")
    public String editClient(Model model, @PathVariable String clientId) {
        buildClientFormModel(model, adminService.getClientUpdateFormData(clientId));
        return "edit-client";
    }

    @PostMapping("/admin/clients/{clientId}/edit")
    public String updateClient(@PathVariable String clientId,
                               RedirectAttributes redirectAttrs,
                               @ModelAttribute(ServiceConstants.CLIENT) ClientFormData clientForm) {
        try {
            String error = adminService.updateClient(clientId, clientForm);
            if (StringUtils.isNotBlank(error)) {
                redirectAttrs.addFlashAttribute(ServiceConstants.ERROR, error);
                return "redirect:/admin/clients/%s/edit".formatted(clientId);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation while updating client {}: {}", clientId, e.getMessage());
            redirectAttrs.addFlashAttribute(ServiceConstants.ERROR, ErrorMessageConstants.DUPLICATE_RECORD);
            return "redirect:/admin/clients/%s/edit".formatted(clientId);
        }
        redirectAttrs.addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.CLIENT_UPDATED);
        return "redirect:/admin/clients";
    }

    @DeleteMapping(value = "/admin/clients/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteClient(@PathVariable String clientId) {
        adminService.deleteClient(clientId);
        return ResponseEntity.ok("Client deleted successfully");
    }

    // ── Users ──────────────────────────────────────────────

    @GetMapping("/admin/users")
    public String listUsers(Model model,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(defaultValue = "false") boolean showAll,
                            @RequestParam(defaultValue = "firstName") String sort,
                            @RequestParam(defaultValue = "ASC") String direction,
                            @RequestParam(required = false) String searchName,
                            @RequestParam(required = false) String searchLastName) {
        Sort.Direction dir = Sort.Direction.fromOptionalString(direction).orElse(Sort.Direction.ASC);
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));

        String sanitizedName = sanitize(searchName);
        String sanitizedLastName = sanitize(searchLastName);
        boolean hasSearch = sanitizedName != null || sanitizedLastName != null;

        if (hasSearch) {
            model.addAttribute(ServiceConstants.USERS, adminService.searchUsers(sanitizedName, sanitizedLastName, pageable, showAll));
        } else {
            model.addAttribute(ServiceConstants.USERS, adminService.findAllUsers(pageable, showAll));
        }

        model.addAttribute("showAll", showAll);
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDirection", direction);
        model.addAttribute("searchName", sanitizedName);
        model.addAttribute("searchLastName", sanitizedLastName);
        return ServiceConstants.USERS;
    }

    @GetMapping("/admin/users/add")
    public String addUserForm(Model model) {
        model.addAttribute(ServiceConstants.USER, UserFormData.withDefaults());
        return ServiceConstants.ADD_USER;
    }

    @PostMapping("/admin/users/add")
    public String addUser(Model model,
                          RedirectAttributes redirectAttrs,
                          @Valid @ModelAttribute(ServiceConstants.USER) UserFormData userForm,
                          BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors()
                    .stream()
                    .map(FieldError::getDefaultMessage)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY);
            model.addAttribute(ServiceConstants.ERROR, errorMessage);
            model.addAttribute(ServiceConstants.USER, userForm);
            return ServiceConstants.ADD_USER;
        }
        try {
            String error = adminService.saveUser(userForm);
            if (StringUtils.isNotBlank(error)) {
                model.addAttribute(ServiceConstants.ERROR, error);
                model.addAttribute(ServiceConstants.USER, userForm);
                return ServiceConstants.ADD_USER;
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation while saving user. Exception: {}", e.getMessage());
            model.addAttribute(ServiceConstants.ERROR, ErrorMessageConstants.DUPLICATE_RECORD);
            model.addAttribute(ServiceConstants.USER, userForm);
            return ServiceConstants.ADD_USER;
        }
        redirectAttrs.addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.USER_SAVED);
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/users/{userId}/edit")
    public String editUser(Model model, @PathVariable Long userId) {
        model.addAttribute(ServiceConstants.USER, adminService.getUserUpdateForm(userId));
        return "edit-user";
    }

    @PostMapping("/admin/users/{userId}/edit")
    public String updateUser(@PathVariable Long userId,
                             @Valid @ModelAttribute(ServiceConstants.USER) UserFormData userForm,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttrs) {
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors()
                    .stream()
                    .map(FieldError::getDefaultMessage)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY);
            redirectAttrs.addFlashAttribute(ServiceConstants.ERROR, errorMessage);
            return "redirect:/admin/users/%d/edit".formatted(userId);
        }
        try {
            adminService.updateUser(userId, userForm);
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation while updating user {}: Exception: {}", userId, e.getMessage());
            redirectAttrs.addFlashAttribute(ServiceConstants.ERROR, ErrorMessageConstants.DUPLICATE_RECORD);
            return "redirect:/admin/users/%d/edit".formatted(userId);
        }
        redirectAttrs.addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.USER_UPDATED);
        return "redirect:/admin/users";
    }

    @DeleteMapping(value = "/admin/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    // ── Sessions ───────────────────────────────────────────

    @GetMapping("/admin/login-attempts")
    public String listLoginAttempts(Model model,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String searchUserId) {
        Pageable pageable = PageRequest.of(page, size);
        String sanitizedUserId = sanitize(searchUserId);

        if (sanitizedUserId != null) {
            try {
                Long userId = Long.valueOf(sanitizedUserId);
                model.addAttribute(ServiceConstants.ATTEMPTS, adminService.searchLoginAttempts(userId, pageable));
            } catch (NumberFormatException _) {
                sanitizedUserId = null;
                model.addAttribute(ServiceConstants.ATTEMPTS, adminService.findAllLoginAttempts(pageable));
            }
        } else {
            model.addAttribute(ServiceConstants.ATTEMPTS, adminService.findAllLoginAttempts(pageable));
        }

        model.addAttribute("searchUserId", sanitizedUserId);
        return "login-attempts";
    }

    @GetMapping("/admin/sessions")
    public String listLoggedInUsers(Model model) {
        model.addAttribute("userSessionMap", adminService.getActiveUserSessions());
        return "user-sessions";
    }

    @GetMapping("/admin/sessions/{sessionId}/evict")
    public String evictSession(@PathVariable java.util.UUID sessionId, RedirectAttributes redirectAttrs) {
        redirectAttrs.addFlashAttribute(ServiceConstants.MESSAGE, adminService.evictSession(sessionId));
        return "redirect:/admin/sessions";
    }

    @GetMapping("/admin/sessions/logout")
    public String evictAllSession(RedirectAttributes redirectAttrs) {
        adminService.evictAllSessions();
        redirectAttrs.addFlashAttribute(ServiceConstants.MESSAGE, ErrorMessageConstants.SESSION_EVICTED);
        return "redirect:/admin/sessions";
    }

    /**
     * Trims the input and returns {@code null} when the value is blank.
     */
    private String sanitize(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }

    private void buildClientFormModel(Model model, ClientUpdateFormData formData) {
        model.addAttribute(GrantType.GRANTS, formData.availableGrants());
        model.addAttribute(AuthorityType.AUTHS, formData.availableAuthorities());
        model.addAttribute(ServiceConstants.CLIENT, formData.clientForm());
    }

    private String addClientFormWithError(Model model, ClientFormData clientForm, String error) {
        buildClientFormModel(model, adminService.buildClientFormModel(clientForm));
        model.addAttribute(ServiceConstants.ERROR, error);
        model.addAttribute("secret", adminService.generateSecret());
        return "add-client";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, RedirectAttributes redirectAttrs) {
        log.error("Unexpected error in admin panel: {}", ex.getMessage(), ex);
        redirectAttrs.addFlashAttribute(ServiceConstants.ERROR, "An unexpected error occurred: " + ex.getMessage());
        return "redirect:/admin";
    }
}
