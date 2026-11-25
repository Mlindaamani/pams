package com.company.procurement.organization.services;

import java.util.UUID;

public record SelfProfileCommand(String displayName, UUID departmentId, UUID managerId) {}
