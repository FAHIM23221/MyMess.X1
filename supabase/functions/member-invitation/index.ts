import { createClient } from "jsr:@supabase/supabase-js@2";

const JSON_HEADERS = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const response = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });

const safeRequestFailure = () =>
  response(200, {
    status: "REQUEST_REJECTED",
    code: "AUTHORIZATION_VALIDATION_CAPACITY_OR_DUPLICATE",
  });

const safeDeliveryFailure = (invitationId: string, code: string) =>
  response(200, {
    status: "DELIVERY_FAILED",
    invitation_id: invitationId,
    code,
    dependency: "SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP",
  });

const handleRequest = async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: JSON_HEADERS });
  }
  if (request.method !== "POST") {
    return response(405, { status: "METHOD_NOT_ALLOWED" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    return response(503, {
      status: "SERVER_CONFIGURATION_MISSING",
      dependency: "EDGE_AUTH_ADMIN_RUNTIME_ENVIRONMENT",
    });
  }

  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    return response(401, { status: "UNAUTHORIZED" });
  }

  let body: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) {
      return response(413, { status: "PAYLOAD_TOO_LARGE" });
    }
    body = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return response(400, { status: "INVALID_JSON" });
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: authData, error: authError } = await userClient.auth.getUser();
  if (authError || !authData.user) {
    return response(401, { status: "UNAUTHORIZED" });
  }
  const actor = authData.user;
  const serviceClient = createClient(supabaseUrl, serviceRoleKey);
  const action = typeof body.action === "string" ? body.action : "create";

  let invitationId: string;
  let email: string;
  let resultStatus: string;

  if (action === "resend") {
    if (typeof body.invitation_id !== "string") {
      return response(400, { status: "INVALID_INVITATION_ID" });
    }
    invitationId = body.invitation_id;
    const { data, error } = await userClient.rpc("prepare_member_invitation_resend", {
      target_invitation_id: invitationId,
    });
    if (error) return safeRequestFailure();
    const row = Array.isArray(data) ? data[0] : data;
    if (!row || typeof row.result_status !== "string") {
      return response(502, { status: "UNCONFIRMED_BACKEND_RESPONSE" });
    }
    resultStatus = row.result_status;
    if (resultStatus === "ACTIVE") {
      return response(200, { status: "ACTIVE", invitation_id: invitationId });
    }
    const { data: invitation, error: invitationError } = await serviceClient
      .from("member_invitations")
      .select("email")
      .eq("id", invitationId)
      .single();
    if (invitationError || typeof invitation?.email !== "string") {
      return response(502, { status: "UNCONFIRMED_BACKEND_RESPONSE" });
    }
    email = invitation.email;
  } else if (action === "create") {
    const requiredStrings = [
      "full_name",
      "email",
      "phone_number",
      "member_id",
      "join_date",
      "intended_role",
      "client_request_id",
    ];
    if (requiredStrings.some((key) => typeof body[key] !== "string")) {
      return response(400, { status: "INVALID_MEMBER_FIELDS" });
    }
    email = (body.email as string).trim().toLowerCase();
    const { data, error } = await userClient.rpc("request_member_onboarding", {
      full_name: body.full_name,
      email,
      phone_number: body.phone_number,
      member_id: body.member_id,
      join_date: body.join_date,
      intended_role: body.intended_role,
      client_request_id: body.client_request_id,
    });
    if (error) return safeRequestFailure();
    const row = Array.isArray(data) ? data[0] : data;
    if (!row || typeof row.invitation_id !== "string" || typeof row.result_status !== "string") {
      return response(502, { status: "UNCONFIRMED_BACKEND_RESPONSE" });
    }
    invitationId = row.invitation_id;
    resultStatus = row.result_status;
    if (resultStatus === "ACTIVE") {
      return response(200, { status: "ACTIVE", invitation_id: invitationId });
    }
    if (resultStatus === "SENT") {
      return response(200, {
        status: "INVITATION_SENT",
        invitation_id: invitationId,
        idempotent: true,
      });
    }
  } else {
    return response(400, { status: "UNSUPPORTED_ACTION" });
  }

  if (resultStatus !== "PENDING") {
    return response(502, { status: "UNCONFIRMED_BACKEND_RESPONSE" });
  }

  // Only the server runtime receives the privileged Auth Admin client. No password,
  // action link, provider error, or credential is returned to Android or written to logs.
  const { data: inviteData, error: inviteError } = await serviceClient.auth.admin.inviteUserByEmail(
    email,
    { redirectTo: "smarttransparentmess://auth" },
  );

  if (inviteError || !inviteData?.user?.id) {
    const outcome = inviteError ? "FAILED" : "UNCONFIRMED";
    const code = inviteError ? "AUTH_INVITE_REJECTED" : "AUTH_INVITE_UNCONFIRMED";
    const { error: finalizeError } = await serviceClient.rpc(
      "finalize_member_invitation_delivery",
      {
        target_invitation_id: invitationId,
        actor_user_id: actor.id,
        outcome,
        error_code: code,
      },
    );
    if (finalizeError) {
      return response(200, {
        status: "DELIVERY_UNCONFIRMED",
        invitation_id: invitationId,
        code: "DELIVERY_STATE_UNCONFIRMED",
        dependency: "SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP",
      });
    }
    return inviteError
      ? safeDeliveryFailure(invitationId, code)
      : response(200, {
          status: "DELIVERY_UNCONFIRMED",
          invitation_id: invitationId,
          code,
          dependency: "SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP",
        });
  }

  const { error: finalizeError } = await serviceClient.rpc(
    "finalize_member_invitation_delivery",
    {
      target_invitation_id: invitationId,
      actor_user_id: actor.id,
      outcome: "SENT",
      error_code: null,
    },
  );
  if (finalizeError) {
    return response(200, {
      status: "DELIVERY_UNCONFIRMED",
      invitation_id: invitationId,
      code: "DELIVERY_STATE_UNCONFIRMED",
      dependency: "SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP",
    });
  }

  return response(200, { status: "INVITATION_SENT", invitation_id: invitationId });
};

Deno.serve(async (request) => {
  try {
    return await handleRequest(request);
  } catch {
    // A thrown runtime/provider error can happen before or after an Auth call.
    // Do not label it as SMTP delivery failure or expose provider internals.
    return response(500, {
      status: "REQUEST_UNCONFIRMED",
      code: "EDGE_OPERATION_UNCONFIRMED",
      dependency: "MEMBER_INVITATION_FUNCTION",
    });
  }
});
