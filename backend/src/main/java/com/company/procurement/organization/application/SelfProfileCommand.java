package com.company.procurement.organization.application;

import java.util.UUID;

public record SelfProfileCommand(String displayName, UUID departmentId, UUID managerId) {}
