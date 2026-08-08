package com.cinemaseat.web;

import jakarta.validation.constraints.NotBlank;

public record HoldRequest(
        @NotBlank String userId
) {}