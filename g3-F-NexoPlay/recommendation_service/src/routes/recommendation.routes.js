import { Router } from "express";
import { recommendationService } from "../services/recommendation.service.js";

const router = Router();

router.get("/", async (req, res, next) => {
  try {
    const userId = req.headers["x-user-id"];
    if (userId && (typeof userId !== "string" || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(userId))) {
      return res.status(400).json({ error: "Invalid user identity" });
    }
    const result = await recommendationService.getRecommendations({
      userId,
      limit: req.query.limit,
      offset: req.query.offset
    });

    res.status(200).json(result);
  } catch (error) {
    next(error);
  }
});

export default router;
